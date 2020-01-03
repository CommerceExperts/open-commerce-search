package de.cxp.ocs.elasticsearch;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.LocaleUtils;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.metadata.AliasMetaData;

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
import de.cxp.ocs.util.OnceInAWhileRunner;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ElasticsearchIndexer extends AbstractIndexer {

	private final String	INDEX_DELIMITER		= "-";
	private final Pattern	INDEX_NAME_PATTERN	= Pattern.compile("\\" + INDEX_DELIMITER + "(\\d+)$");

	private static String[] TEMPLATES = { "german_structured_search", "structured_search" };

	private final IndexConfiguration		properties;
	private final ElasticsearchIndexClient	client;

	private Map<String, Field> fields;

	public ElasticsearchIndexer(IndexConfiguration indexConf, RestHighLevelClient restClient, List<DataPreProcessor> dataProcessors) {
		super(dataProcessors, new CombiFieldBuilder(indexConf.getFieldConfiguration().getFields()));
		this.properties = indexConf;
		this.client = new ElasticsearchIndexClient(restClient);
	}

	@Override
	protected boolean isImportRunning(String indexName) {
		Map<String, Set<AliasMetaData>> aliases = client.getAliases(indexName + INDEX_DELIMITER + "*");
		return (aliases.size() > 1);
	}

	/**
	 * checks to which actual index this "nice indexName (alias)" points to.
	 * Expects a indexName ending with a number and will return a new index name
	 */
	@Override
	protected String initNewIndex(final String indexName, String locale) {
		String finalIndexName = getLocalizedIndexName(indexName, LocaleUtils.toLocale(locale));
		finalIndexName = getNumberedIndexName(finalIndexName);

		client.createFreshIndex(finalIndexName);

		return finalIndexName;
	}

	private String getLocalizedIndexName(String basename, Locale locale) {
		if (locale == null) {
			locale = Locale.ROOT;
		}

		String lang = locale.getLanguage().toLowerCase();

		String normalizedBasename = basename.trim()
				.toLowerCase(locale)
				.replaceAll("[^a-z0-9_-]", "-");

		if (lang.isEmpty() || normalizedBasename.endsWith("-" + lang)) {
			return normalizedBasename;
		}
		else {
			return normalizedBasename + INDEX_DELIMITER + lang;
		}
	}

	private String getNumberedIndexName(String indexName) {
		Map<String, Set<AliasMetaData>> aliases = client.getAliases(indexName + "*");
		if (aliases.isEmpty()) return indexName + INDEX_DELIMITER + "1";

		String oldIndexName = aliases.keySet().iterator().next();
		Matcher indexNameMatcher = INDEX_NAME_PATTERN.matcher(oldIndexName);
		if (indexNameMatcher.find()) {
			int oldIndexNumber = Integer.parseInt(indexNameMatcher.group(1));
			return indexName + INDEX_DELIMITER + String.valueOf(oldIndexNumber + 1);
		}
		else {
			log.warn("initilized first numbered index, although index already exists! {}");
			return indexName + INDEX_DELIMITER + "1";
		}
	}

	@Override
	protected void addToIndex(ImportSession session, List<Document> bulk) throws Exception {
		client.indexRecords(
				session.finalIndexName,
				bulk.stream()
						.map(this::toMasterItem)
						.filter(Objects::nonNull)
						.iterator());
	}

	private MasterItem toMasterItem(Document doc) {
		final Map<String, Object> sourceMasterData = doc.getData();
		final Optional<Field> idField = properties.getFieldConfiguration().getIdField();
		if (idField.isPresent() && sourceMasterData.containsKey(idField.get().getName())) {
			final MasterItem targetMaster = new MasterItem(sourceMasterData.get(idField.get().getName())
					.toString());
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
		else {
			// TODO throw exception to cancel indexing?
			OnceInAWhileRunner.runAgainAfter(() -> log.error(
					"Can't create index as the ID field is missing in the configuration."), "ID_FIELD_MISSING",
					ChronoUnit.SECONDS, 30);
			return null;
		}
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
	public boolean done(ImportSession session) {
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

	@Override
	public boolean cancel(ImportSession session) {
		return client.deleteIndex(session.temporaryIndexName, true);
	}

}
