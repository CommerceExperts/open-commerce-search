package de.cxp.ocs.elasticsearch;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.common.settings.Settings;

import de.cxp.ocs.AbstractIndexer;
import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.conf.IndexConfiguration;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.elasticsearch.model.IndexableItem;
import de.cxp.ocs.elasticsearch.model.MasterItem;
import de.cxp.ocs.elasticsearch.model.VariantItem;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import de.cxp.ocs.preprocessor.CombiFieldBuilder;
import de.cxp.ocs.preprocessor.DataPreProcessor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ElasticsearchIndexer extends AbstractIndexer {

	private final String	INDEX_DELIMITER		= "-";
	private final String	INDEX_PREFIX		= "ocs" + INDEX_DELIMITER;
	private final Pattern	INDEX_NAME_PATTERN	= Pattern.compile(Pattern.quote(INDEX_PREFIX) + "(\\d+)\\" + INDEX_DELIMITER);

	private final ElasticsearchIndexClient client;

	private Map<String, Field> fields;

	public ElasticsearchIndexer(IndexConfiguration indexConf, RestHighLevelClient restClient, List<DataPreProcessor> dataProcessors) {
		super(dataProcessors, new CombiFieldBuilder(indexConf.getFieldConfiguration().getFields()));
		fields = indexConf.getFieldConfiguration().getFields();
		client = new ElasticsearchIndexClient(restClient);
	}

	@Override
	protected boolean isImportRunning(String indexName) {
		if (indexName.startsWith(INDEX_PREFIX)) {
			Optional<Settings> settings = client.getSettings(indexName);
			return settings.map(s -> "-1".equals(s.get("index.refresh_interval"))).orElse(false);
		}
		else {
			Map<String, Set<AliasMetaData>> aliases = client.getAliases(INDEX_PREFIX + "*" + INDEX_DELIMITER + indexName + "*");
			return (aliases.size() > 1);
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

		client.createFreshIndex(finalIndexName);

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
		Map<String, Set<AliasMetaData>> aliases = client.getAliases(indexName);
		if (aliases.size() == 0) return getNumberedIndexName(localizedIndexName, 1);

		String oldIndexName = aliases.keySet().iterator().next();
		Matcher indexNameMatcher = INDEX_NAME_PATTERN.matcher(oldIndexName);
		if (indexNameMatcher.find()) {
			int oldIndexNumber = Integer.parseInt(indexNameMatcher.group(1));
			return getNumberedIndexName(localizedIndexName, oldIndexNumber + 1);
		}
		else {
			log.warn("initilized first numbered index, although index already exists! {}");
			return getNumberedIndexName(localizedIndexName, 1);
		}
	}

	private String getNumberedIndexName(String localizedIndexName, int number) {
		return INDEX_PREFIX + String.valueOf(number) + INDEX_DELIMITER + localizedIndexName;
	}

	@Override
	protected void addToIndex(ImportSession session, List<Document> bulk) throws Exception {
		client.indexRecords(
				session.temporaryIndexName,
				bulk.stream()
						.map(this::toMasterItem)
						.filter(Objects::nonNull)
						.iterator());
	}

	private MasterItem toMasterItem(Document doc) {
		// TODO: validate document (e.g. require IDs etc.)
		final Map<String, Object> sourceMasterData = doc.getData();
		final MasterItem targetMaster = new MasterItem(doc.getId());
		extractSourceValues(sourceMasterData, targetMaster);

		if (doc instanceof Product) {
			for (final Document variantProduct : ((Product) doc).getVariants()) {
				final Map<String, Object> sourceVariantData = variantProduct.getData();
				final VariantItem targetVariant = new VariantItem(targetMaster);
				extractSourceValues(sourceVariantData, targetVariant);
				targetMaster.getVariants().add(targetVariant);
			}
		}
		return targetMaster;
	}

	private void extractSourceValues(final Map<String, Object> sourceData, final IndexableItem targetItem) {
		boolean isVariant = (targetItem instanceof VariantItem);
		for (final Field field : fields.values()) {
			if ((isVariant && field.isVariantLevel() || !isVariant && field.isMasterLevel())) {
				Object value = sourceData.get(field.getName());
				if (value != null) {
					targetItem.setValue(field, value);
				}
			}
		}
	}

	@Override
	public boolean deploy(ImportSession session) {
		try {
			// TODO: move those values into configuration
			client.finalizeIndex(session.temporaryIndexName, 1, "5s");
		}
		catch (IOException e) {
			log.error("can't finish import because index couldn't be flushed");
			return false;
		}

		Map<String, Set<AliasMetaData>> currentAliasState = client.getAliases(session.finalIndexName);

		String oldIndexName = null;
		if (currentAliasState != null && !currentAliasState.isEmpty()) {
			oldIndexName = currentAliasState.keySet().iterator().next();
			if (currentAliasState.size() > 1) {
				log.warn("found more than one index pointing to alias {}", session.finalIndexName);
			}

			if (oldIndexName.equals(session.temporaryIndexName)) return false;
		}

		try {
			client.updateAlias(session.finalIndexName, oldIndexName, session.temporaryIndexName);

			if (oldIndexName != null) {
				client.deleteIndex(oldIndexName, false);
			}
			return true;
		}
		catch (Exception ex) {
			return false;
		}
	}

	public void deleteIndex(String indexName) {
		client.deleteIndex(indexName, false);
	}

}
