package de.cxp.ocs.elasticsearch.query.builder;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.elasticsearch.client.RestHighLevelClient;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.config.QueryConfiguration;
import de.cxp.ocs.config.QueryConfiguration.QueryCondition;
import de.cxp.ocs.elasticsearch.model.query.AnalyzedQuery;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueries.*;
import de.cxp.ocs.plugin.ExtensionSupplierRegistry;
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
	private final SearchContext			context;

	private List<QueryConfiguration> queryConfigs;

	private Map<String, QueryConfiguration> queryConfigIndex = new HashMap<>();

	private final Map<String, Supplier<? extends ESQueryFactory>> knownQueryFactories;

	public ESQueryFactoryBuilder(RestHighLevelClient restClient, SearchContext context, Map<String, Supplier<? extends ESQueryFactory>> esQueryFactorySuppliers) {
		this.restClient = restClient;
		this.indexName = context.config.getIndexName();
		this.context = context;
		this.queryConfigs = context.config.getQueryConfigs();
		this.queryConfigs.forEach(qc -> queryConfigIndex.put(qc.getName(), qc));
		
		ExtensionSupplierRegistry<ESQueryFactory> esQueryFactoryRegistry = new ExtensionSupplierRegistry<ESQueryFactory>();
		esQueryFactoryRegistry.register(PredictionQueryFactory.class, () -> new PredictionQueryFactory(new QueryPredictor(restClient, indexName)));
		esQueryFactoryRegistry.register(ConfigurableQueryFactory.class, ConfigurableQueryFactory::new);
		esQueryFactoryRegistry.register(NgramQueryFactory.class, NgramQueryFactory::new);
		esQueryFactoryRegistry.register(DefaultQueryFactory.class, DefaultQueryFactory::new);
		knownQueryFactories = esQueryFactoryRegistry.getExtensionSuppliers();
		knownQueryFactories.putAll(esQueryFactorySuppliers);
	}

	public ConditionalQueries build() {
		queryConfigs = context.config.getQueryConfigs();
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
			if (queryBuilder.queryBuilder != null) {
				predicatesAndBuilders.add(queryBuilder);
			}
		}
		return new ConditionalQueries(predicatesAndBuilders);
	}

	private Predicate<AnalyzedQuery> createPredicate(QueryCondition condition) {
		List<Predicate<AnalyzedQuery>> collectedPredicates = new ArrayList<>();
		if (condition.getMatchingRegex() != null) {
			collectedPredicates.add(new PatternCondition(condition.getMatchingRegex()));
		}
		if (condition.getMaxTermCount() < Integer.MAX_VALUE || condition.getMinTermCount() > 1) {
			collectedPredicates.add(new TermCountCondition(condition.getMinTermCount(), condition.getMaxTermCount()));
		}
		if (condition.getMaxQueryLength() < Integer.MAX_VALUE && condition.getMaxQueryLength() > 0) {
			collectedPredicates.add(new QueryLengthCondition(condition.getMaxQueryLength()));
		}
		if (collectedPredicates.size() > 0) {
			return new ComposedPredicate(collectedPredicates);
		}
		else if (collectedPredicates.size() == 0) return (arr) -> true;
		else return collectedPredicates.get(0);
	}

	private ESQueryFactory createQueryFactory(QueryConfiguration queryConf) {
		String strategyName = queryConf.getStrategy();
		Supplier<? extends ESQueryFactory> queryFactorySupplier = knownQueryFactories.get(strategyName);

		// Fallback: check if the strategy name + "Factory" is the actual class
		// we're looking for
		if (queryFactorySupplier == null && !strategyName.endsWith("Factory")) {
			String factoryName = strategyName + "Factory";
			queryFactorySupplier = knownQueryFactories.get(factoryName);
		}

		if (queryFactorySupplier == null) {
			log.error("No ESQueryFactory implementation found for configured strategy {}", strategyName);
			return null;
		}
		ESQueryFactory esQueryFactory = queryFactorySupplier.get();
		esQueryFactory.initialize(queryConf.getName(), queryConf.getSettings(), queryConf.getWeightedFields(), context.getFieldConfigIndex());

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

}
