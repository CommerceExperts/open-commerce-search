package de.cxp.ocs.elasticsearch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.DocWriteRequest.OpType;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestStatus;

import de.cxp.ocs.DocumentMapper;
import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.api.indexer.UpdateIndexService;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.IndexSettings;
import de.cxp.ocs.indexer.AbstractIndexer;
import de.cxp.ocs.indexer.model.IndexableItem;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.spi.indexer.DocumentPostProcessor;
import de.cxp.ocs.spi.indexer.DocumentPreProcessor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ElasticsearchIndexer extends AbstractIndexer {

	private final String	INDEX_DELIMITER		= "-";
	private final String	INDEX_PREFIX		= "ocs" + INDEX_DELIMITER;
	private final Pattern	INDEX_NAME_PATTERN	= Pattern.compile(Pattern.quote(INDEX_PREFIX) + "(\\d+)\\" + INDEX_DELIMITER);

	private final IndexSettings				indexSettings;
	private final RestHighLevelClient		restClient;
	private final ElasticsearchIndexClient	indexClient;

	public ElasticsearchIndexer(
			IndexSettings settings,
			FieldConfigIndex fieldConfAccess,
			RestHighLevelClient restClient,
			List<DocumentPreProcessor> preProcessors,
			List<DocumentPostProcessor> postProcessors) {
		super(preProcessors, postProcessors, fieldConfAccess);
		this.restClient = restClient;
		this.indexSettings = settings;
		indexClient = new ElasticsearchIndexClient(restClient);
	}

	ElasticsearchIndexer(
			FieldConfigIndex fieldConfAccess,
			ElasticsearchIndexClient indexClient,
			List<DocumentPreProcessor> dataProcessors,
			List<DocumentPostProcessor> postProcessors) {
		super(dataProcessors, postProcessors, fieldConfAccess);
		this.restClient = null;
		this.indexSettings = new IndexSettings();
		this.indexClient = indexClient;
	}

	@Override
	public boolean indexExists(String indexName) {
		if (isInternalIndexName(indexName)) {
			Optional<Settings> settings = indexClient.getSettings(indexName);
			return settings.isPresent();
		}
		else {
			Map<String, Set<AliasMetadata>> aliases = getIndexNameRelatedAliases(indexName);
			return !aliases.isEmpty();
		}
	}

	@Override
	public boolean isImportRunning(String indexName) {
		if (isInternalIndexName(indexName)) {
			Optional<Settings> settings = indexClient.getSettings(indexName);
			return settings.map(s -> "-1".equals(s.get("index.refresh_interval"))).orElse(false);
		}
		else {
			Map<String, Set<AliasMetadata>> aliases = getIndexNameRelatedAliases(indexName);
			return (aliases.size() > 1 || (aliases.size() == 1 && aliases.values().iterator().next().isEmpty()));
		}
	}

	private boolean isInternalIndexName(String indexName) {
		return indexName.startsWith(INDEX_PREFIX);
	}

	private Map<String, Set<AliasMetadata>> getIndexNameRelatedAliases(String indexName) {
		String indexSearchPattern = INDEX_PREFIX + "*" + INDEX_DELIMITER + normalizeIndexName(indexName) + INDEX_DELIMITER + "*";
		return indexClient.getAliases(indexSearchPattern);
	}

	/**
	 * checks to which actual index this "nice indexName (alias)" points to.
	 * Expects a indexName ending with a number and will return a new index name
	 */
	@Override
	protected String initNewIndex(final String indexName, String locale) throws IOException {
		String localizedIndexName = getLocalizedIndexName(indexName, locale);
		String finalIndexName = getNextIndexName(indexName, localizedIndexName);

		try {
			log.info("creating index {}", finalIndexName);
			indexClient.createFreshIndex(finalIndexName);
		}
		catch (Exception e) {
			throw new IOException("failed to initialize index " + finalIndexName, e);
		}

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

	private String getLocalizedIndexName(String basename, String locale) {
		return getLocalizedIndexName(basename, LocaleUtils.toLocale(locale));
	}

	private String getLocalizedIndexName(String basename, Locale locale) {
		if (locale == null) {
			locale = Locale.ROOT;
		}

		String lang = locale.getLanguage().toLowerCase();

		String normalizedBasename = normalizeIndexName(basename);

		if (lang.isEmpty() || normalizedBasename.endsWith(INDEX_DELIMITER + lang)) {
			return normalizedBasename;
		}
		else {
			return normalizedBasename + INDEX_DELIMITER + lang;
		}
	}

	private String normalizeIndexName(String basename) {
		return StringUtils.strip(
				basename.toLowerCase(Locale.ROOT)
						.replaceAll("[^a-z0-9_\\-\\.]+", INDEX_DELIMITER),
				INDEX_DELIMITER);
	}

	private String getNextIndexName(String indexName, String localizedIndexName) {
		Map<String, Set<AliasMetadata>> aliases = indexClient.getAliases(INDEX_PREFIX + "*" + INDEX_DELIMITER + localizedIndexName);
		if (aliases.isEmpty()) return getNumberedIndexName(localizedIndexName, 1);

		int oldIndexNumber = aliases.keySet().stream().mapToInt(this::extractIndexNumber).max().orElse(1);
		String numberedIndexName = getNumberedIndexName(localizedIndexName, oldIndexNumber + 1);
		if (oldIndexNumber == 0) {
			log.warn("initilized first numbered index {}, although similar indexes already exists! {}", numberedIndexName, aliases);
		}

		return numberedIndexName;
	}

	private int extractIndexNumber(String fullIndexName) {
		Matcher indexNameMatcher = INDEX_NAME_PATTERN.matcher(fullIndexName);
		if (indexNameMatcher.find()) {
			return Integer.parseInt(indexNameMatcher.group(1));
		}
		else {
			return 0;
		}
	}

	private String getNumberedIndexName(String localizedIndexName, int number) {
		return INDEX_PREFIX + number + INDEX_DELIMITER + localizedIndexName;
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
			try {
				return indexClient.indexRecords(session.temporaryIndexName, bulk)
						.map(this::getSuccessCount)
						.orElse(0);
			}
			catch (ElasticsearchStatusException ese) {
				if (ese.getMessage().contains("Data too large")) {
					log.warn("BulkSize seems to high for index request to {} with {} items. Bulk Indexation will be split in two parts...", session.finalIndexName, bulk.size());
					return indexClient.indexRecordsChunkwise(session.temporaryIndexName, bulk.iterator(), bulk.size() / 2)
							.stream()
							.collect(Collectors.summingInt(this::getSuccessCount));
				}
				else {
					throw ese;
				}
			}
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
		}
		else {
			return bulkResponse.getItems().length;
		}
	}

	@Override
	public boolean deploy(ImportSession session) {

		try {
			boolean success = indexClient.finalizeIndex(session.temporaryIndexName, indexSettings.replicaCount, indexSettings.refreshInterval);
			log.info("applying live settings to index {} was {}successful", session.temporaryIndexName, success ? "" : "not ");
		}
		catch (IOException e) {
			log.error("can't finish import because index {} couldn't be flushed", session.temporaryIndexName, e);
			return false;
		}

		Map<String, Set<AliasMetadata>> currentAliasState = indexClient.getAliases(session.finalIndexName);

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
			ClusterHealthStatus indexHealth = indexClient.waitUntilHealthy(session.temporaryIndexName, indexSettings.waitTimeMsForHealthyIndex);
			if (ClusterHealthStatus.RED.equals(indexHealth)) {
				log.error("Index {} not healthy after {}ms! Won't deploy!", session.temporaryIndexName, indexSettings.waitTimeMsForHealthyIndex);
				return false;
			}

			long docCount = indexClient.getDocCount(session.temporaryIndexName);
			if (docCount < indexSettings.minimumDocumentCount) {
				log.error("new index version {} for index {} has only {} documents indexed, which is not the required minimum document count of {}",
						session.temporaryIndexName, session.finalIndexName, docCount, indexSettings.minimumDocumentCount);
				return false;
			}
		}
		catch (Exception e) {
			log.error("Exception while trying to access new index {}", session.temporaryIndexName, e);
			return false;
		}

		boolean result = false;
		try {
			indexClient.updateAlias(session.finalIndexName, oldIndexName, session.temporaryIndexName);
			log.info("successful deployed index {} to internal index {}", session.finalIndexName, session.temporaryIndexName);
			result = true;

			if (oldIndexName != null) {
				log.info("deleting old index {}", oldIndexName);
				indexClient.deleteIndex(oldIndexName, false);
			}
		}
		catch (Exception ex) {
			log.warn("Exception during deployment of index {} to internal index {}", session.finalIndexName, session.temporaryIndexName, ex);
		}
		return result;
	}

	protected void deleteIndex(String indexName) {
		indexClient.deleteIndex(indexName, false);
	}

	@Override
	protected UpdateIndexService.Result _patch(String index, IndexableItem doc) {
		// do some validation?
		try {
			DocWriteResponse.Result result = indexClient.updateDocument(index, doc).getResult();
			return translateResult(result);
		}
		catch (ElasticsearchStatusException statusEx) {
			log.error("update for document with id {} failed", doc.getId(), statusEx);
			return translateResult(statusEx.status());
		}
		catch (IOException ioe) {
			log.error("update for document with id {} failed", doc.getId(), ioe);
			throw new UncheckedIOException(ioe);
		}
		catch (RuntimeException re) {
			log.error("update for document with id {} failed", doc.getId(), re);
			throw re;
		}
	}

	@Override
	protected UpdateIndexService.Result _put(String indexName, Boolean replaceExisting, IndexableItem doc) {
		try {
			if (replaceExisting) {
				DocWriteResponse.Result result = indexClient.indexRecord(indexName, doc, OpType.INDEX).getResult();
				return translateResult(result);
			}
			else {
				DocWriteResponse.Result result = indexClient.indexRecord(indexName, doc, OpType.CREATE).getResult();
				return translateResult(result);
			}
		}
		catch (ElasticsearchStatusException statusEx) {
			log.error("indexing document with id {} failed", doc.getId(), statusEx);
			return translateResult(statusEx.status());
		}
		catch (IOException ioe) {
			log.error("indexing document with id {} failed", doc.getId(), ioe);
			throw new UncheckedIOException(ioe);
		}
		catch (RuntimeException esEx) {
			log.error("indexing document with id {} failed", doc.getId(), esEx);
			throw esEx;
		}
	}

	@Override
	public Map<String, UpdateIndexService.Result> deleteDocuments(String indexName, List<String> ids) {
		try {
			List<DeleteResponse> deleteResponses = indexClient.deleteDocuments(indexName, ids);
			Map<String, UpdateIndexService.Result> deleteResults = new HashMap<>(deleteResponses.size());
			for (DeleteResponse delResp : deleteResponses) {
				deleteResults.put(delResp.getId(), translateResult(delResp.getResult()));
			}
			return deleteResults;
		}
		catch (IOException e) {
			log.error("deleting documents with ids {} failed", ids, e);
			throw new UncheckedIOException(e);
		}
		catch (RuntimeException esEx) {
			log.error("deleting documents with ids {} failed", ids, esEx);
			throw esEx;
		}
	}

	@Override
	protected Document _get(String indexName, @NonNull String id) {
		try {
			GetResponse esDoc = restClient.get(new GetRequest(indexName, id), RequestOptions.DEFAULT);
			return esDoc.getVersion() == -1 ? null : DocumentMapper.mapToOriginalDocument(id, esDoc.getSource(), getFieldConfIndex());
		}
		catch (IOException ioe) {
			log.error("fetching document with id {} failed", id, ioe);
			throw new UncheckedIOException(ioe);
		}
		catch (RuntimeException esEx) {
			log.error("fetching document with id {} failed", id, esEx);
			throw esEx;
		}
	}

	private UpdateIndexService.Result translateResult(org.elasticsearch.action.DocWriteResponse.Result result) {
		switch (result) {
			case CREATED:
				return UpdateIndexService.Result.CREATED;
			case DELETED:
				return UpdateIndexService.Result.DELETED;
			case NOOP:
				return UpdateIndexService.Result.NOOP;
			case NOT_FOUND:
				return UpdateIndexService.Result.NOT_FOUND;
			case UPDATED:
				return UpdateIndexService.Result.UPDATED;
		}
		return null;
	}

	private UpdateIndexService.Result translateResult(RestStatus status) {
		switch (status) {
			case CREATED:
				return UpdateIndexService.Result.CREATED;
			case NOT_FOUND:
				return UpdateIndexService.Result.NOT_FOUND;
			default:
				return UpdateIndexService.Result.DISMISSED;
		}
	}

	@Override
	protected void cleanupAbandonedImports(String indexName, int minAgeSeconds) {
		new AbandonedIndexCleanupTask(indexClient, indexName, minAgeSeconds).run();
	}

}
