package de.cxp.ocs.elasticsearch.facets;

import static de.cxp.ocs.elasticsearch.facets.FacetFactory.getLabel;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FacetType;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.config.IndexedField;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.util.SearchQueryBuilder;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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
	private final Map<String, FacetConfig>					facetsBySourceField		= new HashMap<>();
	private final List<FacetCreator>						facetCreators			= new ArrayList<>();
	private final Map<FacetCreatorClassifier, FacetCreator>	facetCreatorsByTypes	= new HashMap<>();

	private int maxFacets;

	private final List<FacetFilter> facetFilters = new ArrayList<>();

	@Data
	@RequiredArgsConstructor
	private static class FacetCreatorClassifier {

		final static FacetCreatorClassifier hierarchicalFacet = new FacetCreatorClassifier(false, FacetType.HIERARCHICAL.name());

		final static FacetCreatorClassifier	masterIntervalFacet	= new FacetCreatorClassifier(false, FacetType.INTERVAL.name());
		final static FacetCreatorClassifier	masterRangeFacet	= new FacetCreatorClassifier(false, FacetType.RANGE.name());
		final static FacetCreatorClassifier	masterTermFacet		= new FacetCreatorClassifier(false, FacetType.TERM.name());

		final static FacetCreatorClassifier	variantIntervalFacet	= new FacetCreatorClassifier(true, FacetType.INTERVAL.name());
		final static FacetCreatorClassifier	variantRangeFacet		= new FacetCreatorClassifier(true, FacetType.RANGE.name());
		final static FacetCreatorClassifier	variantTermFacet		= new FacetCreatorClassifier(true, FacetType.TERM.name());

		final boolean onVariantLevel;

		@NonNull
		// not using the FacetType enum here to support custom facet types
		// TODO: implement custom facet type support
		String facetType;
	}

	@Setter
	@NonNull
	private Set<String> excludeFields = Collections.emptySet();

	public FacetConfigurationApplyer(SearchContext context) {
		defaultTermFacetConfigProvider = getDefaultFacetConfigProvider(context.config.getFacetConfiguration().getDefaultTermFacetConfiguration());
		defaultNumberFacetConfigProvider = getDefaultFacetConfigProvider(context.config.getFacetConfiguration().getDefaultNumberFacetConfiguration());

		maxFacets = context.config.getFacetConfiguration().getMaxFacets();

		loadFacetConfig(context);

		facetFilters.add(new FacetCoverageFilter());
		facetFilters.add(new FacetSizeFilter());
		facetFilters.add(new FacetDependencyFilter(facetsBySourceField));
	}

	public void loadFacetConfig(SearchContext context) {
		// I tried to do this whole method in a more generic way, but such code
		// is less readable even if shorter
		Map<String, FacetConfig> hierarchicalFacets = new HashMap<>();
		Map<String, FacetConfig> intervalFacets = new HashMap<>();
		Map<String, FacetConfig> rangeFacets = new HashMap<>();
		Map<String, FacetConfig> termFacets = new HashMap<>();
		Map<String, FacetConfig> variantIntervalFacets = new HashMap<>();
		Map<String, FacetConfig> variantRangeFacets = new HashMap<>();
		Map<String, FacetConfig> variantTermFacets = new HashMap<>();

		Set<Field> ignoredFields = new HashSet<>();

		// put facet configs into according maps
		for (FacetConfig facetConfig : context.config.getFacetConfiguration().getFacets()) {
			// TODO: make this general with FacetCreatorFactory
			if ("INDEX_NAME".equals(facetConfig.getType())) {
				facetsBySourceField.put(facetConfig.getSourceField(), facetConfig);
				facetCreators.add(new IndexNameFacetCreator(facetConfig));
				continue;
			}

			Optional<Field> sourceField = context.getFieldConfigIndex().getField(facetConfig.getSourceField());

			if (!sourceField.isPresent()) {
				log.warn("facet {} configured for field {}, but that field does not exist. Facet won't be created",
						facetConfig.getLabel(), facetConfig.getSourceField());
				continue;
			}

			Field facetField = sourceField.get();
			if (facetsBySourceField.put(facetConfig.getSourceField(), facetConfig) != null) {
				log.warn("multiple facets based on same source field are not supported!"
						+ " Overwriting facet config for source field {}",
						facetConfig.getSourceField());
			}

			if (facetConfig.getType() == null) {
				facetConfig.setType(getDefaultFacetType(facetField.getType()).name());
			}
			else if ("ignore".equalsIgnoreCase(facetConfig.getType())) {
				ignoredFields.add(facetField);
				continue;
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

			if (FieldType.CATEGORY.equals(facetField.getType())) {
				if (!FacetType.HIERARCHICAL.name().equalsIgnoreCase(facetConfig.getType())) {
					log.warn("facet {} based on *category* field {} was configured as {} facet, but only 'hierarchical' type is supported",
							facetConfig.getLabel(), facetField.getName(), facetConfig.getType());
					facetConfig.setType(FacetType.HIERARCHICAL.name());
				}
				if (!facetField.isVariantLevel()) {
					hierarchicalFacets.put(facetField.getName(), facetConfig);
				}
				else {
					log.warn("*category* facet {} based on field {} does not work, as it's a variant field",
							facetConfig.getLabel(), facetField.getName());
				}
			}
			else if (FieldType.NUMBER.equals(facetField.getType())) {
				if (FacetType.RANGE.name().equalsIgnoreCase(facetConfig.getType())) {
					if (facetField.isMasterLevel()) rangeFacets.put(facetField.getName(), facetConfig);
					if (facetField.isVariantLevel()) variantRangeFacets.put(facetField.getName(), facetConfig);
				}
				else {
					if (!FacetType.INTERVAL.name().equalsIgnoreCase(facetConfig.getType())) {
						log.warn("facet {} based on *number* field {} was configured as {} facet, but only 'interval' or 'range' type is supported."
								+ " To create a 'term' facet on numeric data, the according field has to be indexed as *string* field.",
								facetConfig.getLabel(), facetField.getName(), facetConfig.getType());
					}
					if (facetField.isMasterLevel()) intervalFacets.put(facetField.getName(), facetConfig);
					if (facetField.isVariantLevel()) variantIntervalFacets.put(facetField.getName(), facetConfig);
				}
			}
			else {
				if (!FacetType.TERM.name().equals(facetConfig.getType())) {
					log.warn("facet {} based on *{}* field {} was configured as {} facet, but only 'term' type is supported",
							facetConfig.getLabel(), facetField.getType(), facetField.getName(), facetConfig.getType());
				}
				if (facetField.isVariantLevel()) variantTermFacets.put(facetField.getName(), facetConfig);
				if (facetField.isMasterLevel()) termFacets.put(facetField.getName(), facetConfig);
			}
		}

		// build all generic facet creators passing the specific configs to it
		CategoryFacetCreator categoryFacetCreator = new CategoryFacetCreator(hierarchicalFacets, null);
		categoryFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(ignoredFields, FieldType.CATEGORY));
		facetCreators.add(categoryFacetCreator);
		facetCreatorsByTypes.put(FacetCreatorClassifier.hierarchicalFacet, categoryFacetCreator);

		Locale locale = context.config.getLocale();
		NestedFacetCreator masterTermFacetCreator = new TermFacetCreator(termFacets, defaultTermFacetConfigProvider, locale)
				.setMaxFacets(maxFacets);
		masterTermFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(ignoredFields, FieldType.STRING));
		facetCreators.add(masterTermFacetCreator);
		facetCreatorsByTypes.put(FacetCreatorClassifier.masterTermFacet, masterTermFacetCreator);

		String defaultNumberFacetType = defaultNumberFacetConfigProvider.apply("").getType();

		List<FacetCreator> masterNumberFacetCreators = initNumberFacetCreators(intervalFacets, rangeFacets, ignoredFields, defaultNumberFacetType, false);
		facetCreators.addAll(masterNumberFacetCreators);

		List<FacetCreator> variantFacetCreators = new ArrayList<>();
		NestedFacetCreator variantTermFacetCreator = new TermFacetCreator(variantTermFacets, defaultTermFacetConfigProvider, locale).setMaxFacets(maxFacets);
		variantTermFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(ignoredFields, FieldType.STRING));
		variantFacetCreators.add(variantTermFacetCreator);
		facetCreatorsByTypes.put(FacetCreatorClassifier.variantTermFacet, new VariantFacetCreator(Collections.singleton(variantTermFacetCreator)));

		List<FacetCreator> variantNumberFacetCreators = initNumberFacetCreators(variantIntervalFacets, variantRangeFacets, ignoredFields, defaultNumberFacetType, true);
		variantFacetCreators.addAll(variantNumberFacetCreators);

		// consolidated variant facet creator
		facetCreators.add(new VariantFacetCreator(variantFacetCreators));
	}

	/**
	 * Depending on the default number facet type, we also have to init the creator for the other type if there are
	 * specific configs for it. We then also have to exclude the fields for the specific creator from the default
	 * creator. And of course those creators have to be registered accordingly with the correct type.
	 * That all is handled here.
	 * 
	 * @param intervalFacetConfigs
	 *        either the ones from master or variant level
	 * @param rangeFacetConfigs
	 *        either the ones from master or variant level
	 * @param allIgnoredFields
	 *        a set of all ignored facet-fields
	 * @param defaultNumberFacetType
	 *        the type of the default numeric facet
	 * @param isVariantLevel
	 *        true if those configs are for variant level, otherwise false.
	 * @return
	 */
	private List<FacetCreator> initNumberFacetCreators(Map<String, FacetConfig> intervalFacetConfigs, Map<String, FacetConfig> rangeFacetConfigs, Set<Field> allIgnoredFields, String defaultNumberFacetType, boolean isVariantLevel) {
		List<FacetCreator> initializedFacetCreators = new ArrayList<>();

		NestedFacetCreator defaultNumberFacetCreator;
		// set for all non-default facets that should be excluded from default facet generation
		HashSet<String> nonDefaultNumberFacetFields;

		if (FacetType.RANGE.name().equals(defaultNumberFacetType)) {
			NestedFacetCreator rangeFacetCreator = new RangeFacetCreator(rangeFacetConfigs, defaultNumberFacetConfigProvider).setMaxFacets(maxFacets);
			facetCreatorsByTypes.put(isVariantLevel ? FacetCreatorClassifier.variantRangeFacet : FacetCreatorClassifier.masterRangeFacet, rangeFacetCreator);

			defaultNumberFacetCreator = rangeFacetCreator;
			nonDefaultNumberFacetFields = new HashSet<>(intervalFacetConfigs.keySet());

			// add facet creator for explicit facet creation that has different type than the default
			if (!intervalFacetConfigs.isEmpty()) {
				IntervalFacetCreator intervalFacetCreator = new IntervalFacetCreator(intervalFacetConfigs, null);
				intervalFacetCreator.setExplicitFacetCreator(true);
				intervalFacetCreator.setMaxFacets(maxFacets);
				intervalFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(allIgnoredFields, FieldType.NUMBER));
				initializedFacetCreators.add(intervalFacetCreator);
				facetCreatorsByTypes.put(isVariantLevel ? FacetCreatorClassifier.variantIntervalFacet : FacetCreatorClassifier.masterIntervalFacet, intervalFacetCreator);
			}
		}
		else {
			if (!FacetType.INTERVAL.name().equals(defaultNumberFacetType)) {
				log.error("Invalid type for default number facet configuration: '{}' - will consider 'INTERVAL' as default", defaultNumberFacetType);
			}
			NestedFacetCreator intervalFacetCreator = new IntervalFacetCreator(intervalFacetConfigs, defaultNumberFacetConfigProvider).setMaxFacets(maxFacets);
			facetCreatorsByTypes.put(isVariantLevel ? FacetCreatorClassifier.variantIntervalFacet : FacetCreatorClassifier.masterIntervalFacet, intervalFacetCreator);

			defaultNumberFacetCreator = intervalFacetCreator;
			nonDefaultNumberFacetFields = new HashSet<>(rangeFacetConfigs.keySet());

			// add facet creator for explicit facet creation that has different type than the default
			if (!rangeFacetConfigs.isEmpty()) {
				// TODO: FacetCreators that run on the same nested field, should be
				// grouped to use a single nested-aggregation for their aggregations
				RangeFacetCreator rangeFacetCreator = new RangeFacetCreator(rangeFacetConfigs, null);
				rangeFacetCreator.setExplicitFacetCreator(true);
				rangeFacetCreator.setMaxFacets(maxFacets);
				rangeFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(allIgnoredFields, FieldType.NUMBER));
				initializedFacetCreators.add(rangeFacetCreator);
				facetCreatorsByTypes.put(isVariantLevel ? FacetCreatorClassifier.variantRangeFacet : FacetCreatorClassifier.masterRangeFacet, rangeFacetCreator);
			}

		}

		nonDefaultNumberFacetFields.addAll(getNamesOfMatchingFields(allIgnoredFields, FieldType.NUMBER));
		defaultNumberFacetCreator.setGeneralExcludedFields(nonDefaultNumberFacetFields);
		initializedFacetCreators.add(defaultNumberFacetCreator);

		return initializedFacetCreators;
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

	private Set<String> getNamesOfMatchingFields(Set<Field> ignoredFields, FieldType fieldType) {
		return ignoredFields.stream()
				.filter(f -> fieldType.equals(f.getType()))
				.map(Field::getName)
				.collect(Collectors.toSet());
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
			FilterContext filterContext, SearchQueryBuilder linkBuilder) {
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

	private List<Facet> facetsFromFilteredAggregations(Aggregations aggregations, FilterContext filterContext, SearchQueryBuilder linkBuilder) {
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

	private List<Facet> facetsFromUnfilteredAggregations(Aggregations aggregations, FilterContext filterContext, SearchQueryBuilder linkBuilder) {
		Map<String, Facet> facets = new HashMap<>();
		collectFacets(facets, facetCreators, aggregations, filterContext, linkBuilder);
		return new ArrayList<>(facets.values());
	}

	private void collectFacets(Map<String, Facet> facets, List<FacetCreator> facetCreators, Aggregations aggregations, FilterContext filterContext,
			SearchQueryBuilder linkBuilder) {
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
