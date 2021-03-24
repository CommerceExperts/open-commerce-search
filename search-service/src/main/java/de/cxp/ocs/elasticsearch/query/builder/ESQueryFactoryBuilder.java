package de.cxp.ocs.elasticsearch.query.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.elasticsearch.client.RestHighLevelClient;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.config.InternalSearchConfiguration;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.config.QueryConfiguration;
import de.cxp.ocs.config.QueryConfiguration.QueryCondition;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueries.ComposedPredicate;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueries.ConditionalQuery;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueries.PatternCondition;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueries.TermCountCondition;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.spi.search.ESQueryFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * utility for Searcher class to extract the creation of ESQueryBuilders based
 * on the given configuration
 */
@Slf4j
public class ESQueryFactoryBuilder {

	@NonNull
	private final RestHighLevelClient			restClient;
	@NonNull
	private final String						indexName;
	@NonNull
	private final InternalSearchConfiguration	config;

	private List<QueryConfiguration> queryConfigs;

	private Map<String, QueryConfiguration> queryConfigIndex = new HashMap<>();

	private Map<String, Supplier<? extends ESQueryFactory>> knownQueryFactories = new HashMap<>();

	public ESQueryFactoryBuilder(RestHighLevelClient restClient, InternalSearchConfiguration config, List<ESQueryFactory> additionalQueryFactories) {
		this.restClient = restClient;
		this.indexName = config.provided.getIndexName();
		this.config = config;
		this.queryConfigs = config.provided.getQueryConfigs();
		this.queryConfigs.forEach(qc -> queryConfigIndex.put(qc.getName(), qc));
		
		registerESQueryFactory(PredictionQueryFactory.class, () -> new PredictionQueryFactory(new QueryPredictor(restClient, indexName)));
		registerESQueryFactory(ConfigurableQueryFactory.class, ConfigurableQueryFactory::new);
		registerESQueryFactory(NgramQueryFactory.class, NgramQueryFactory::new);
		registerESQueryFactory(DefaultQueryFactory.class, DefaultQueryFactory::new);
		additionalQueryFactories.forEach(this::registerCustomESQueryFactory);
	}

	@SuppressWarnings("unchecked")
	private <F extends ESQueryFactory> void registerCustomESQueryFactory(F factory) {
		Class<F> clazz = (Class<F>) factory.getClass();
		registerESQueryFactory(clazz, () -> {
			try {
				return clazz.newInstance();
			}
			catch (InstantiationException | IllegalAccessException e) {
				throw new IllegalStateException("Custom ESQueryFactory " + clazz + " misses a required default construtor", e);
			}
		});
	}

	private <F extends ESQueryFactory> void registerESQueryFactory(Class<F> clazz, Supplier<F> supplier) {
		String queryName = clazz.getSimpleName().replaceFirst("Factory", "");
		if (knownQueryFactories.put(queryName, supplier) != null) {
			log.warn("the simple query name {} from the factory class {} was already registered and overwritten!",
					queryName, clazz.getCanonicalName());
		}
		if (knownQueryFactories.put(clazz.getSimpleName(), supplier) != null) {
			log.warn("the simple class name {} from the factory class {} was already registered and overwritten!",
					clazz.getSimpleName(), clazz.getCanonicalName());
		}
		if (knownQueryFactories.put(clazz.getCanonicalName(), supplier) != null) {
			log.warn("the ESQueryFactory class {} was already registered and overwritten!",
					clazz.getCanonicalName());
		}
	}

	public ConditionalQueries build() {
		queryConfigs = config.provided.getQueryConfigs();
		if (queryConfigs == null || queryConfigs.size() == 0) {
			return new ConditionalQueries(new DefaultQueryFactory());
		}
		if (queryConfigs.size() == 1) {
			return new ConditionalQueries(createQueryFactory(queryConfigs.get(0)));
		}

		List<ConditionalQuery> predicatesAndBuilders = new LinkedList<>();
		for (QueryConfiguration queryConf : queryConfigs) {
			ConditionalQuery queryBuilder = new ConditionalQuery();
			queryBuilder.predicate = createPredicate(queryConf.getCondition());
			queryBuilder.queryBuilder = createQueryFactory(queryConf);
			predicatesAndBuilders.add(queryBuilder);
		}
		return new ConditionalQueries(predicatesAndBuilders);
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

	private ESQueryFactory createQueryFactory(QueryConfiguration queryConf) {
		Supplier<? extends ESQueryFactory> queryFactorySupplier = knownQueryFactories.get(queryConf.getStrategy());
		if (queryFactorySupplier == null) {
			log.error("No ESQueryFactory implementation found for configured strategy {}", queryConf.getStrategy());
			return null;
		}
		ESQueryFactory esQueryFactory = queryFactorySupplier.get();
		Map<String, Float> fieldWeights = loadFields(queryConf.getWeightedFields());
		esQueryFactory.initialize(queryConf.getName(), queryConf.getSettings(), fieldWeights, config.getFieldConfigIndex());

		// Special case for PredictionQueryFactory.
		// Not sure if this should be supported generally
		if (esQueryFactory instanceof PredictionQueryFactory) {
			getFallbackQueryBuilder(queryConf).ifPresent(((PredictionQueryFactory) esQueryFactory)::setFallbackQueryBuilder);
		}
		
		return esQueryFactory;
	}

	private Optional<ESQueryFactory> getFallbackQueryBuilder(QueryConfiguration queryConf) {
		ESQueryFactory fallbackQueryBuilder = null;
		String fallbackSearchName = queryConf.getSettings().get(QueryBuildingSetting.fallbackQuery);
		if (fallbackSearchName != null && !fallbackSearchName.isEmpty() && queryConfigIndex.containsKey(fallbackSearchName)) {
			QueryConfiguration fallbackQueryConf = queryConfigIndex.get(fallbackSearchName);
			fallbackQueryBuilder = createQueryFactory(fallbackQueryConf);
		}
		return Optional.ofNullable(fallbackQueryBuilder);
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