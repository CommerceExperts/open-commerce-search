package de.cxp.ocs.elasticsearch.query.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.elasticsearch.client.RestHighLevelClient;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.config.InternalSearchConfiguration;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.config.QueryConfiguration;
import de.cxp.ocs.config.QueryConfiguration.QueryCondition;
import de.cxp.ocs.elasticsearch.query.ESQueryBuilder;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueryBuilder.BuilderWithCondition;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueryBuilder.ComposedPredicate;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueryBuilder.PatternCondition;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueryBuilder.TermCountCondition;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * utility for Searcher class to extract the creation of ESQueryBuilders based
 * on the given configuration
 */
@Slf4j
public class ESQueryBuilderFactory {

	@NonNull
	private final RestHighLevelClient			restClient;
	@NonNull
	private final String						indexName;
	@NonNull
	private final InternalSearchConfiguration	config;

	private List<QueryConfiguration> queryConfigs;

	private Map<String, QueryConfiguration> queryConfigIndex = new HashMap<>();

	public ESQueryBuilderFactory(RestHighLevelClient restClient, InternalSearchConfiguration config) {
		this.restClient = restClient;
		this.indexName = config.provided.getIndexName();
		this.config = config;
		this.queryConfigs = config.provided.getQueryConfigs();
		this.queryConfigs.forEach(qc -> queryConfigIndex.put(qc.getName(), qc));
	}

	private QueryPredictor getMetaPreFetcher(Map<QueryBuildingSetting, String> settings) {
		// TODO: add a cache-wrapper to avoid multiple data fetches for the
		// same query (maybe this has to be done inside the
		// QueryMetaFetcher)
		QueryPredictor preFetcher = new QueryPredictor(restClient, indexName);
		preFetcher.setAnalyzer(settings.get(QueryBuildingSetting.analyzer));
		return preFetcher;
	}

	public ConditionalQueryBuilder build() {
		queryConfigs = config.provided.getQueryConfigs();
		if (queryConfigs == null || queryConfigs.size() == 0) {
			return new ConditionalQueryBuilder(new DefaultQueryBuilder());
		}
		if (queryConfigs.size() == 1) {
			return new ConditionalQueryBuilder(createQueryBuilder(queryConfigs.get(0), null));
		}

		List<BuilderWithCondition> predicatesAndBuilders = new LinkedList<>();
		for (QueryConfiguration queryConf : queryConfigs) {
			BuilderWithCondition queryBuilder = new BuilderWithCondition();

			ESQueryBuilder fallbackQueryBuilder = getFallbackQueryBuilder(queryConf);

			queryBuilder.predicate = createPredicate(queryConf.getCondition());
			queryBuilder.queryBuilder = createQueryBuilder(queryConf, fallbackQueryBuilder);
			queryBuilder.queryBuilder.setName(queryConf.getName());
			predicatesAndBuilders.add(queryBuilder);
		}
		return new ConditionalQueryBuilder(predicatesAndBuilders);
	}

	private ESQueryBuilder getFallbackQueryBuilder(QueryConfiguration queryConf) {
		ESQueryBuilder fallbackQueryBuilder = null;
		String fallbackSearchName = queryConf.getSettings().get(QueryBuildingSetting.fallbackQuery);
		if (fallbackSearchName != null && !fallbackSearchName.isEmpty() && queryConfigIndex.containsKey(fallbackSearchName)) {
			QueryConfiguration fallbackQueryConf = queryConfigIndex.get(fallbackSearchName);
			fallbackQueryBuilder = createQueryBuilder(fallbackQueryConf, null);
		}
		return fallbackQueryBuilder;
	}

	private Predicate<List<QueryStringTerm>> createPredicate(QueryCondition condition) {
		List<Predicate<List<QueryStringTerm>>> collectedPredicates = new ArrayList<>();
		if (condition.getMatchingRegex() != null) {
			collectedPredicates.add(new PatternCondition(condition.getMatchingRegex()));
		}
		if (condition.getMaxTermCount() < Integer.MAX_VALUE || condition.getMinTermCount() > 1) {
			collectedPredicates.add(new TermCountCondition(condition.getMinTermCount(), condition.getMaxTermCount()));
		}
		if (collectedPredicates.size() > 0) {
			return new ComposedPredicate(collectedPredicates);
		}
		else if (collectedPredicates.size() == 0) return (arr) -> true;
		else return collectedPredicates.get(0);
	}

	private ESQueryBuilder createQueryBuilder(QueryConfiguration queryConf, ESQueryBuilder fallbackQueryBuilder) {
		switch (queryConf.getStrategy()) {
			case PredictionQuery:
				PredictionQueryBuilder qmbQB = new PredictionQueryBuilder(
						getMetaPreFetcher(queryConf.getSettings()),
						queryConf.getSettings(),
						loadFields(queryConf.getWeightedFields()));
				qmbQB.setFallbackQueryBuilder(fallbackQueryBuilder);
				return qmbQB;
			case ConfigurableQuery:
				return new ConfigurableQueryBuilder(queryConf.getSettings(), loadFields(queryConf.getWeightedFields()));
			case NgramQueryBuilder:
				return new NgramQueryBuilder(
						queryConf.getSettings(),
						loadFields(queryConf.getWeightedFields()),
						config.getFieldConfigIndex().getFieldsByUsage(FieldUsage.Search));
			case DefaultQuery:
			default:
				return new DefaultQueryBuilder();
		}
	}

	private Map<String, Float> loadFields(Map<String, Float> weightedFields) {
		Map<String, Float> validatedFields = new HashMap<>();
		FieldConfigIndex fieldConfig = config.getFieldConfigIndex();
		weightedFields.forEach((fieldNamePattern, weight) -> {
			if (isSearchableField(fieldConfig, fieldNamePattern)) {
				validatedFields.put(fieldNamePattern, weight);
			}
			else {
				log.warn("ignored field {} for query builder, because its not configured for search", fieldNamePattern);
			}
		});
		return validatedFields;
	}

	private boolean isSearchableField(FieldConfigIndex fieldConfig, String fieldNamePattern) {
		String fieldName = fieldNamePattern.split("[\\.]")[0];
		Field fieldConf = null;
		if (fieldName.endsWith("*")) {
			for (Field field : fieldConfig.getFieldsByUsage(FieldUsage.Search).values()) {
				if (field != null && field.getName() != null
						&& field.getName().startsWith(fieldName.substring(0, fieldName.length() - 1))) {
					fieldConf = field;
					break;
				}
			}
		}
		else {
			fieldConf = fieldConfig.getField(fieldName).orElse(null);
		}
		return (fieldConf != null && fieldConf.getUsage().contains(FieldUsage.Search));
	}
}
