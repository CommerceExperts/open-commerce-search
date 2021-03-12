package de.cxp.ocs.elasticsearch.query.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;

import org.elasticsearch.client.RestHighLevelClient;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.config.QueryConfiguration;
import de.cxp.ocs.config.QueryConfiguration.QueryCondition;
import de.cxp.ocs.config.SearchConfiguration;
import de.cxp.ocs.elasticsearch.query.ESQueryBuilder;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueryBuilder.BuilderWithCondition;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueryBuilder.ComposedPredicate;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueryBuilder.PatternCondition;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueryBuilder.TermCountCondition;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * utility for Searcher class to extract the creation of ESQueryBuilders based
 * on the given configuration
 */
@Slf4j
@RequiredArgsConstructor
public class ESQueryBuilderFactory {

	@NonNull
	private final RestHighLevelClient	restClient;
	@NonNull
	private final String				index;
	@NonNull
	private final SearchConfiguration	config;

	private Map<String, QueryConfiguration> queryConfigs;

	private QueryPredictor getMetaPreFetcher(Map<QueryBuildingSetting, String> settings) {
		// TODO: add a cache-wrapper to avoid multiple data fetches for the
		// same query (maybe this has to be done inside the
		// QueryMetaFetcher)
		QueryPredictor preFetcher = new QueryPredictor(restClient, index);
		preFetcher.setAnalyzer(settings.get(QueryBuildingSetting.analyzer));
		return preFetcher;
	}

	public ConditionalQueryBuilder build() {
		queryConfigs = config.getQueryConfigs();
		if (queryConfigs == null || queryConfigs.size() == 0) {
			return new ConditionalQueryBuilder(new DefaultQueryBuilder());
		}
		if (queryConfigs.size() == 1) {
			return new ConditionalQueryBuilder(createQueryBuilder(queryConfigs.values().iterator().next(), null));
		}

		List<BuilderWithCondition> predicatesAndBuilders = new LinkedList<>();
		for (Entry<String, QueryConfiguration> queryNameAndConf : queryConfigs.entrySet()) {
			BuilderWithCondition queryBuilder = new BuilderWithCondition();

			ESQueryBuilder fallbackQueryBuilder = getFallbackQueryBuilder(queryNameAndConf.getKey(), queryConfigs);

			queryBuilder.predicate = createPredicate(queryNameAndConf.getValue().getCondition());
			queryBuilder.queryBuilder = createQueryBuilder(queryNameAndConf.getValue(), fallbackQueryBuilder);
			queryBuilder.queryBuilder.setName(queryNameAndConf.getKey());
			predicatesAndBuilders.add(queryBuilder);
		}
		return new ConditionalQueryBuilder(predicatesAndBuilders);
	}

	private ESQueryBuilder getFallbackQueryBuilder(String queryName, Map<String, QueryConfiguration> queryConfigs) {
		ESQueryBuilder fallbackQueryBuilder = null;
		QueryConfiguration queryConf = queryConfigs.get(queryName);
		String fallbackSearchName = queryConf.getSettings().get(QueryBuildingSetting.fallbackQuery);

		// XXX: maybe it would be nice to allow fallback queries for fallback
		// queries, however cyclic dependencies and self-references should be
		// prevented!
		if (fallbackSearchName != null && !fallbackSearchName.isEmpty()) {
			QueryConfiguration fallbackQueryConf = queryConfigs.get(fallbackSearchName);
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
						config.getIndexedFieldConfig().getFieldsByUsage(FieldUsage.Search));
			case DefaultQuery:
			default:
				return new DefaultQueryBuilder();
		}
	}

	private Map<String, Float> loadFields(Map<String, Float> weightedFields) {
		Map<String, Float> validatedFields = new HashMap<>();
		FieldConfigIndex fieldConfig = config.getIndexedFieldConfig();
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
