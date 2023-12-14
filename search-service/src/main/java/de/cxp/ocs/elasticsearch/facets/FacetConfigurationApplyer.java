package de.cxp.ocs.elasticsearch.facets;

import static de.cxp.ocs.elasticsearch.facets.FacetFactory.getLabel;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.config.*;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.spi.search.CustomFacetCreator;
import de.cxp.ocs.util.DefaultLinkBuilder;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FacetConfigurationApplyer {

	static final String	EXCLUSIVE_AGG_PREFIX	= "_exclusive_for_";
	static final String	FILTERED_AGG_NAME		= "_filtered";

	/**
	 * meta key to mark facets that should not be counted into the "max facets"
	 * limit
	 */
	static final String IS_MANDATORY_META_KEY = "isMandatory";

	private final Function<String, FacetConfig>				defaultTermFacetConfigProvider;
	private final Function<String, FacetConfig>				defaultNumberFacetConfigProvider;
	private final Map<String, FacetConfig>					facetsBySourceField;
	private final List<FacetCreator>						facetCreators			= new ArrayList<>();
	private final Map<FacetCreatorClassifier, FacetCreator>	facetCreatorsByTypes	= new HashMap<>();

	private int maxFacets;

	private final List<FacetFilter> facetFilters = new ArrayList<>();

	@Setter
	@NonNull
	private Set<String> excludeFields = Collections.emptySet();

	public FacetConfigurationApplyer(SearchContext context, Set<Supplier<? extends CustomFacetCreator>> customFacetCreatorSupplier) {
		FacetConfiguration facetConfiguration = context.config.getFacetConfiguration();
		defaultTermFacetConfigProvider = getDefaultFacetConfigProvider(facetConfiguration.getDefaultTermFacetConfiguration());
		defaultNumberFacetConfigProvider = getDefaultFacetConfigProvider(facetConfiguration.getDefaultNumberFacetConfiguration());
		maxFacets = facetConfiguration.getMaxFacets();

		Map<String, Supplier<? extends CustomFacetCreator>> customFacetCreatorsByType = initCustomFacetCreators(facetConfiguration, customFacetCreatorSupplier);
		facetsBySourceField = loadFacetConfig(context, customFacetCreatorsByType);

		facetFilters.add(new FacetCoverageFilter());
		facetFilters.add(new FacetSizeFilter());
		facetFilters.add(new FacetDependencyFilter(facetsBySourceField));
	}

	private Map<String, Supplier<? extends CustomFacetCreator>> initCustomFacetCreators(FacetConfiguration facetConfiguration, Set<Supplier<? extends CustomFacetCreator>> customFacetCreatorSupplier) {

		Map<String, Supplier<? extends CustomFacetCreator>> customFacetCreatorsByType = new HashMap<>();
		customFacetCreatorSupplier.forEach(supplier -> {
			CustomFacetCreator customFacetCreator = supplier.get();
			Supplier<?> previousSupplier = customFacetCreatorsByType.put(customFacetCreator.getFacetType(), supplier);
			if (previousSupplier != null) {
				log.warn("For facet-type '{}' there are two conflicting CustomFacetCreator implementation: {} replaced {}! Make sure to use unique facet types!",
						customFacetCreator.getFacetType(), customFacetCreator.getClass().getCanonicalName(), previousSupplier.get().getClass().getCanonicalName());
			}
		});

		// generate fixed-configured interval facets:
		// facets with type 'interval_N' get a special treatment
		for (FacetConfig facetConfig : facetConfiguration.getFacets()) {
			String facetType = facetConfig.getType();
			if (facetType != null && facetType.startsWith("interval_") && facetType.matches("interval_\\d+") && !customFacetCreatorsByType.containsKey(facetType)) {
				int interval = Integer.parseInt(facetType.substring("interval_".length()));
				// one instance per interval: different facets with the same interval can be handled by the same
				ConfiguredIntervalFacetCreator fixedIntervalFacetCreator = new ConfiguredIntervalFacetCreator(interval);
				customFacetCreatorsByType.put(facetType, () -> fixedIntervalFacetCreator);
			}
		}

		return customFacetCreatorsByType;
	}

	public Map<String, FacetConfig> loadFacetConfig(SearchContext context, Map<String, Supplier<? extends CustomFacetCreator>> customFacetCreators) {
		Map<String, FacetConfig> _facetsBySourceField = new HashMap<>();

		FacetCreatorInitializer creatorInit = new FacetCreatorInitializer(customFacetCreators, context.config, defaultTermFacetConfigProvider, defaultNumberFacetConfigProvider);

		// put facet configs into according maps
		for (FacetConfig facetConfig : context.config.getFacetConfiguration().getFacets()) {

			Optional<Field> sourceField = context.getFieldConfigIndex().getField(facetConfig.getSourceField());

			// TODO: make this general with FacetCreatorFactory
			if ("INDEX_NAME".equals(facetConfig.getType())) {
				// TODO: add validation for related field name
				// String filterName = null;
				// if (sourceField.isPresent()) {
				// log.warn("facetType=INDEX_NAME for field {} does not make any sense! Please use a non-conflicting
				// name for filtering! Changed it to {}", facetConfig.getSourceField(), filterName);
				// }
				// if (facetConfig.getSourceField() == null) {
				// log.info("For facet with type INDEX_NAME an artificial source field name has to be defined, that is
				// usable for filtering. Automatically choosed name '{}'", );
				// }

				_facetsBySourceField.put(facetConfig.getSourceField(), facetConfig);
				facetCreators.add(new IndexNameFacetCreator(facetConfig));
				continue;
			}

			if (!sourceField.isPresent()) {
				log.warn("facet {} configured for field {}, but that field does not exist. Facet won't be created",
						facetConfig.getLabel(), facetConfig.getSourceField());
				continue;
			}

			Field facetField = sourceField.get();

			if (_facetsBySourceField.put(facetConfig.getSourceField(), facetConfig) != null) {
				log.warn("multiple facets based on same source field are not supported!"
						+ " Overwriting facet config for source field {}",
						facetConfig.getSourceField());
			}

			if (facetField instanceof IndexedField) {
				int valueCardinality = ((IndexedField) facetField).getValueCardinality();
				if (valueCardinality > 0 && valueCardinality < facetConfig.getMinValueCount()) {
					log.warn(
							"facet {} on field {} has minValueCount={}, but there are only {} values indexed at all. Setting minValueCount accordingly.",
							facetConfig.getLabel(), facetConfig.getSourceField(), facetConfig.getMinValueCount(),
							valueCardinality);
					facetConfig.setMinValueCount(valueCardinality);
				}
			}

			creatorInit.addFacet(facetField, facetConfig);
		}

		facetCreatorsByTypes.putAll(creatorInit.init());
		List<FacetCreator> variantFacetCreators = new ArrayList<>();
		for (Entry<FacetCreatorClassifier, FacetCreator> fcEntry : facetCreatorsByTypes.entrySet()) {
			if (fcEntry.getKey().onVariantLevel) {
				if (fcEntry.getValue() instanceof VariantFacetCreator) {
					variantFacetCreators.addAll(((VariantFacetCreator) fcEntry.getValue()).getInnerCreators());
				}
				else {
					variantFacetCreators.add(fcEntry.getValue());
				}
			}
			else {
				facetCreators.add(fcEntry.getValue());
			}
		}
		facetCreators.add(new VariantFacetCreator(variantFacetCreators));

		return Collections.unmodifiableMap(_facetsBySourceField);
	}

	private Function<String, FacetConfig> getDefaultFacetConfigProvider(FacetConfig defaultFacetConf) {
		if (defaultFacetConf.equals(new FacetConfig())) {
			return name -> new FacetConfig(name, name);
		}

		return name -> new FacetConfig(name, name)
				.setExcludeFromFacetLimit(defaultFacetConf.isExcludeFromFacetLimit())
				.setMetaData(defaultFacetConf.getMetaData())
				.setMultiSelect(defaultFacetConf.isMultiSelect())
				.setOptimalValueCount(defaultFacetConf.getOptimalValueCount())
				.setOrder(defaultFacetConf.getOrder())
				.setPreferVariantOnFilter(defaultFacetConf.isPreferVariantOnFilter())
				.setShowUnselectedOptions(defaultFacetConf.isShowUnselectedOptions())
				.setSourceField(name)
				.setType(defaultFacetConf.getType())
				.setValueOrder(defaultFacetConf.getValueOrder())
				.setMinFacetCoverage(defaultFacetConf.getMinFacetCoverage())
				.setMinValueCount(defaultFacetConf.getMinValueCount());
	}

	private FacetType getDefaultFacetType(FieldType type) {
		switch (type) {
			case NUMBER:
				return FacetType.INTERVAL;
			case CATEGORY:
				return FacetType.HIERARCHICAL;
			case COMBI:
			case ID:
			case STRING:
			default:
				return FacetType.TERM;
		}
	}

	/**
	 * <p>
	 * This method uses the initialized FacetCreators to build the right
	 * aggregations in respect of the active post filters.
	 * </p>
	 *
	 * <strong>Background / Details:</strong>
	 * <p>
	 * For facets that should stay the same, even if one of its filters was
	 * selected ("multi-select-facets"), the post filtering feature is used.
	 * This way such facets can be created without their active filter. See
	 * <a href=
	 * "https://www.elastic.co/guide/en/elasticsearch/reference/current/filter-search-results.html#post-filter">Elasticsearch
	 * post filter documentation</a>
	 * </p>
	 * <p>
	 * However this leads to the problem, that other facets are also not
	 * filtered, although they should be. Otherwise they may present filters
	 * that lead to 0 results in combination with the active post filter/s.
	 * </p>
	 * <p>
	 * The (only) solution is to apply the post filters for all those other
	 * aggregations as well, but of course not for the aggregation of the active
	 * post filter.
	 * This gets complicated if there are several post filters for different
	 * multi-select-facets. In that case each according aggregation needs to be
	 * filtered with the other post filters but not the related one.
	 * </p>
	 * <i>Example:</i>
	 * <p>
	 * "brand" and "price" are configured to be multi-select-facets. If for both
	 * facets a filter is applied, the "brand" aggregation must consider the
	 * "price" filter and the "price" aggregation must consider the "brand"
	 * filter. All other aggregations must consider both post filters.
	 * </p>
	 * <p>
	 * More details at http://stackoverflow.com/questions/41369749
	 * </p>
	 *
	 *
	 * @param filterContext
	 *        context that holds the filter queries
	 * @return
	 *         list of filtered and/or unfiltered aggregation builders depending
	 *         on the existance of post filters
	 */
	public List<AggregationBuilder> buildAggregators(FilterContext filterContext) {
		List<AggregationBuilder> aggregators = new ArrayList<>();

		// if there are no post filters, add aggregations without filters
		// => at the getFacets method this has to be considered
		if (filterContext.getPostFilterQueries().isEmpty()) {
			for (FacetCreator creator : facetCreators) {
				aggregators.add(creator.buildAggregation());
			}
		}
		else {
			Map<String, QueryBuilder> postFilters = filterContext.getPostFilterQueries();
			for (String postFilterName : postFilters.keySet()) {
				InternalResultFilter internalFilter = filterContext.getInternalFilters().get(postFilterName);
				QueryBuilder exclusiveFilterQuery = getExclusivePostFilterQuery(postFilterName, internalFilter, postFilters);

				FilterAggregationBuilder filterAgg = AggregationBuilders.filter(EXCLUSIVE_AGG_PREFIX + postFilterName, exclusiveFilterQuery);

				getResponsibleFacetCreators(internalFilter)
						.forEach(facetCreator -> filterAgg.subAggregation(facetCreator.buildIncludeFilteredAggregation(Collections.singleton(postFilterName))));

				aggregators.add(filterAgg);
			}

			// create a filter for all post filters and add all aggregations
			// that are not specialized for all the post filters
			FilterAggregationBuilder fullFilteredAgg = AggregationBuilders.filter(FILTERED_AGG_NAME, filterContext.getJoinedPostFilters());
			for (FacetCreator creator : facetCreators) {
				fullFilteredAgg.subAggregation(creator.buildExcludeFilteredAggregation(filterContext.getPostFilterQueries().keySet()));
			}
			aggregators.add(fullFilteredAgg);
		}

		return aggregators;
	}

	private QueryBuilder getExclusivePostFilterQuery(String postFilterName, InternalResultFilter internalFilter, Map<String, QueryBuilder> postFilters) {
		// and combines that with all other post filters
		QueryBuilder finalAggFilter = FilterContext.joinAllButOne(postFilterName, postFilters)
				.orElse(QueryBuilders.matchAllQuery());
		return finalAggFilter;
	}

	private List<FacetCreator> getResponsibleFacetCreators(InternalResultFilter internalFilter) {
		Field facetField = internalFilter.getField();
		FacetConfig facetConfig = facetsBySourceField.get(facetField.getName());

		String facetType = facetConfig != null ? facetConfig.getType() : null;
		if (facetType == null) {
			facetType = getDefaultFacetType(facetField.getType()).name();
		}

		// handle facets that need to be created on master and variant level
		List<FacetCreator> facetCreators = new ArrayList<>(facetField.isBothLevel() ? 2 : 1);
		if (facetField.isVariantLevel()) {
			FacetCreator facetCreator = facetCreatorsByTypes.get(new FacetCreatorClassifier(true, facetType));
			if (facetCreator != null) {
				facetCreators.add(facetCreator);
			}
		}
		if (facetField.isMasterLevel()) {
			FacetCreator facetCreator = facetCreatorsByTypes.get(new FacetCreatorClassifier(false, facetType));
			if (facetCreator != null) {
				facetCreators.add(facetCreator);
			}
		}
		return facetCreators;
	}

	public List<Facet> getFacets(Aggregations aggregations, long matchCount,
			FilterContext filterContext, DefaultLinkBuilder linkBuilder) {
		List<Facet> facets;

		if (filterContext.getPostFilterQueries().isEmpty()) {
			facets = facetsFromUnfilteredAggregations(aggregations, filterContext, linkBuilder);
		}
		else {
			facets = facetsFromFilteredAggregations(aggregations, filterContext, linkBuilder);
		}

		filterFacets(facets, filterContext, matchCount);

		// sort by order and facet coverage
		facets.sort(new Comparator<Facet>() {

			@Override
			public int compare(Facet o1, Facet o2) {
				// prio 1: configured order
				int compare = Integer.compare(FacetFactory.getOrder(o1), FacetFactory.getOrder(o2));
				// prio 2: prefer facets with filtered value
				if (compare == 0) {
					compare = Boolean.compare(o2.isFiltered(), o1.isFiltered());
				}
				// prio 3: prefer higher facet coverage (reverse natural order)
				if (compare == 0) {
					compare = Long.compare(o2.getAbsoluteFacetCoverage(), o1.getAbsoluteFacetCoverage());
				}
				return compare;
			}

		});

		int actualMaxFacets = maxFacets + (int) facets.stream()
				.filter(f -> (boolean) f.meta.getOrDefault(IS_MANDATORY_META_KEY, false))
				.count();

		return facets.size() > actualMaxFacets ? facets.subList(0, actualMaxFacets) : facets;
	}

	private void filterFacets(List<Facet> facets, FilterContext filterContext, long matchCount) {
		Iterator<Facet> facetIterator = facets.iterator();
		while (facetIterator.hasNext()) {
			Facet facet = facetIterator.next();
			if (facet.isFiltered)
				continue;

			FacetConfig facetConfig = facetsBySourceField.get(facet.getFieldName());
			if (facetConfig == null) {
				if (FacetType.TERM.name().equalsIgnoreCase(facet.type)) {
					facetConfig = defaultTermFacetConfigProvider.apply(facet.getFieldName());
				}
				else {
					facetConfig = defaultNumberFacetConfigProvider.apply(facet.getFieldName());
				}
			}
			if (facetConfig != null) {
				for (FacetFilter facetFilter : facetFilters) {
					if (!facetFilter.isVisibleFacet(facet, facetConfig, filterContext, (int) matchCount)) {
						log.debug("removing facet {} because of filter {}", facetConfig.getLabel(),
								facetFilter.getClass().getSimpleName());
						facetIterator.remove();
						break;
					}
				}
			}
		}
	}

	private List<Facet> facetsFromFilteredAggregations(Aggregations aggregations, FilterContext filterContext, DefaultLinkBuilder linkBuilder) {
		Map<String, Facet> facets = new HashMap<>();

		Filter filteredAggregation = aggregations.get(FILTERED_AGG_NAME);
		if (filteredAggregation != null) {
			collectFacets(facets, facetCreators, filteredAggregation.getAggregations(), filterContext, linkBuilder);
		}

		for (String postFilterName : filterContext.getPostFilterQueries().keySet()) {
			Filter exclusiveAgg = aggregations.get(EXCLUSIVE_AGG_PREFIX + postFilterName);
			if (exclusiveAgg != null && exclusiveAgg.getDocCount() > 0L) {
				List<FacetCreator> matchingFacetCreators = getResponsibleFacetCreators(filterContext.getInternalFilters().get(postFilterName));
				collectFacets(facets, matchingFacetCreators, exclusiveAgg.getAggregations(), filterContext, linkBuilder);
			}
		}

		return new ArrayList<>(facets.values());
	}

	private List<Facet> facetsFromUnfilteredAggregations(Aggregations aggregations, FilterContext filterContext, DefaultLinkBuilder linkBuilder) {
		Map<String, Facet> facets = new HashMap<>();
		collectFacets(facets, facetCreators, aggregations, filterContext, linkBuilder);
		return new ArrayList<>(facets.values());
	}

	private void collectFacets(Map<String, Facet> facets, List<FacetCreator> facetCreators, Aggregations aggregations, FilterContext filterContext,
			DefaultLinkBuilder linkBuilder) {
		Set<String> appliedFilters = filterContext.getInternalFilters().keySet();

		for (FacetCreator fc : facetCreators) {
			Collection<Facet> createdFacets = fc.createFacets(aggregations, filterContext, linkBuilder);

			for (Facet f : createdFacets) {
				Facet previousFacet = facets.get(getLabel(f));

				if (previousFacet != null) {
					Optional<Facet> mergedFacet = fc.mergeFacets(previousFacet, f);
					if (!mergedFacet.isPresent()) {
						log.warn("Not able to merge duplicate facet of label {}! Will drop the one of type {} from field {}",
								getLabel(f), f.type, f.fieldName);
						continue;
					}
					else {
						f = mergedFacet.get();
					}
				}
				if (appliedFilters.contains(f.getFieldName())) {
					f.setFiltered(true);
				}

				FacetConfig facetConfig = facetsBySourceField.get(f.getFieldName());
				if (facetConfig != null && facetConfig.isExcludeFromFacetLimit()) {
					f.meta.put(IS_MANDATORY_META_KEY, true);
				}
				facets.put(getLabel(f), f);
			}
		}
	}

}
