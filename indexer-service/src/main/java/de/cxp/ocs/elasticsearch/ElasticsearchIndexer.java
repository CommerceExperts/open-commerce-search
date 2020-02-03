package de.cxp.ocs.elasticsearch;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.common.settings.Settings;

import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.conf.IndexConfiguration;
import de.cxp.ocs.indexer.AbstractIndexer;
import de.cxp.ocs.indexer.model.IndexableItem;
import de.cxp.ocs.preprocessor.DataPreProcessor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ElasticsearchIndexer extends AbstractIndexer {

	private final String	INDEX_DELIMITER		= "-";
	private final String	INDEX_PREFIX		= "ocs" + INDEX_DELIMITER;
	private final Pattern	INDEX_NAME_PATTERN	= Pattern.compile(Pattern.quote(INDEX_PREFIX) + "(\\d+)\\" + INDEX_DELIMITER);

	private final ElasticsearchIndexClient indexClient;

	public ElasticsearchIndexer(IndexConfiguration indexConf, RestHighLevelClient restClient, List<DataPreProcessor> dataProcessors) {
		super(dataProcessors, indexConf);
		indexClient = new ElasticsearchIndexClient(restClient);
	}

	protected ElasticsearchIndexer(IndexConfiguration indexConf, List<DataPreProcessor> dataProcessors, ElasticsearchIndexClient esIndexClient) {
		super(dataProcessors, indexConf);
		indexClient = esIndexClient;
	}

	@Override
	public boolean isImportRunning(String indexName) {
		if (indexName.startsWith(INDEX_PREFIX)) {
			Optional<Settings> settings = indexClient.getSettings(indexName);
			return settings.map(s -> "-1".equals(s.get("index.refresh_interval"))).orElse(false);
		}
		else {
			Map<String, Set<AliasMetaData>> aliases = indexClient.getAliases(INDEX_PREFIX + "*" + INDEX_DELIMITER + indexName + "*");
			return (aliases.size() > 1 || (aliases.size() == 1 && aliases.values().iterator().next().isEmpty()));
		}
	}

	/**
	 * checks to which actual index this "nice indexName (alias)" points to.
	 * Expects a indexName ending with a number and will return a new index name
	 */
	@Override
	protected String initNewIndex(final String indexName, String locale) {
		String localizedIndexName = getLocalizedIndexName(indexName, LocaleUtils.toLocale(locale));
		String finalIndexName = getNextIndexName(indexName, localizedIndexName);

		log.info("trying to create index {}", finalIndexName);
		indexClient.createFreshIndex(finalIndexName);

		return finalIndexName;
	}

	@Override
	protected void validateSession(ImportSession session) throws IllegalArgumentException {
		if (session.finalIndexName == null || session.temporaryIndexName == null) {
			throw new IllegalArgumentException("invalid session: values missing");
		}
		if (!session.temporaryIndexName.contains(session.finalIndexName)) {
			throw new IllegalArgumentException("invalid session: names missmatch");
		}
	}

	private String getLocalizedIndexName(String basename, Locale locale) {
		if (locale == null) {
			locale = Locale.ROOT;
		}

		String lang = locale.getLanguage().toLowerCase();

		String normalizedBasename = StringUtils.strip(
				basename.toLowerCase(locale)
						.replaceAll("[^a-z0-9_\\-\\.]+", INDEX_DELIMITER),
				INDEX_DELIMITER);

		if (lang.isEmpty() || normalizedBasename.endsWith(INDEX_DELIMITER + lang)) {
			return normalizedBasename;
		}
		else {
			return normalizedBasename + INDEX_DELIMITER + lang;
		}
	}

	private String getNextIndexName(String indexName, String localizedIndexName) {
		Map<String, Set<AliasMetaData>> aliases = indexClient.getAliases(indexName);
		if (aliases.size() == 0) return getNumberedIndexName(localizedIndexName, 1);

		String oldIndexName = aliases.keySet().iterator().next();
		Matcher indexNameMatcher = INDEX_NAME_PATTERN.matcher(oldIndexName);
		String numberedIndexName;
		if (indexNameMatcher.find()) {
			int oldIndexNumber = Integer.parseInt(indexNameMatcher.group(1));
			numberedIndexName = getNumberedIndexName(localizedIndexName, oldIndexNumber + 1);
		}
		else {
			numberedIndexName = getNumberedIndexName(localizedIndexName, 1);
			log.warn("initilized first numbered index {}, although final index already exists! {}", numberedIndexName, oldIndexName);
		}

		return numberedIndexName;
	}

	private String getNumberedIndexName(String localizedIndexName, int number) {
		return INDEX_PREFIX + String.valueOf(number) + INDEX_DELIMITER + localizedIndexName;
	}

	@Override
	protected int addToIndex(ImportSession session, List<IndexableItem> bulk) throws Exception {
		if (bulk.size() > 1000) {
			log.info("Adding {} documents in 1000 chunks to index {}", bulk.size(), session.finalIndexName);
			return indexClient.indexRecordsChunkwise(session.temporaryIndexName, bulk.iterator(), 1000)
					.stream()
					.collect(Collectors.summingInt(this::getSuccessCount));
		}
		else {
			log.info("Adding {} documents to index {}", bulk.size(), session.finalIndexName);
			return indexClient.indexRecords(session.temporaryIndexName, bulk.iterator())
					.map(this::getSuccessCount)
					.orElse(0);
		}
	}

	private int getSuccessCount(BulkResponse bulkResponse) {
		if (bulkResponse.hasFailures()) {
			int success = 0;
			int failures = 0;
			for (BulkItemResponse responseItem : bulkResponse.getItems()) {
				if (responseItem.isFailed()) {
					if (failures++ == 0) {
						log.warn("First failure in bulk: {}", responseItem.getFailureMessage());
					}
				}
				else {
					success++;
				}
			}
			if (failures > 1) {
				log.warn("{} bulk insertions failed. {} successes", failures, success);
			}
			return success;
		} else {
			return bulkResponse.getItems().length;
		}
	}

	@Override
	public boolean deploy(ImportSession session) {
		try {
			// TODO: move those values into configuration
			boolean success = indexClient.finalizeIndex(session.temporaryIndexName, 1, "5s");
			log.info("applying live settings to index {} was {}successful", session.temporaryIndexName, success ? "" : "not ");
		}
		catch (IOException e) {
			log.error("can't finish import because index couldn't be flushed");
			return false;
		}

		Map<String, Set<AliasMetaData>> currentAliasState = indexClient.getAliases(session.finalIndexName);

		String oldIndexName = null;
		if (currentAliasState != null && !currentAliasState.isEmpty()) {
			oldIndexName = currentAliasState.keySet().iterator().next();
			if (currentAliasState.size() > 1) {
				log.warn("found more than one index pointing to alias {}", session.finalIndexName);
			}

			if (oldIndexName.equals(session.temporaryIndexName)) {
				log.info("tried to deploy index {} that is already deployed at name {}", session.temporaryIndexName, session.finalIndexName);
				return false;
			}
		}

		try {
			indexClient.updateAlias(session.finalIndexName, oldIndexName, session.temporaryIndexName);
			log.info("successful deployed index {} to internal index {}", session.finalIndexName, session.temporaryIndexName);

			if (oldIndexName != null) {
				log.info("deleting old index {}", oldIndexName);
				indexClient.deleteIndex(oldIndexName, false);
			}
			return true;
		}
		catch (Exception ex) {
			log.warn("deploying index {} ot internal index {} failed", session.finalIndexName, session.temporaryIndexName, ex);
			return false;
		}
	}

	public void deleteIndex(String indexName) {
		indexClient.deleteIndex(indexName, false);
	}

}
