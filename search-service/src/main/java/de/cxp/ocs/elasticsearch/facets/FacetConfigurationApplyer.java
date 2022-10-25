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

	private final Map<String, FacetConfig> facetsBySourceField = new HashMap<>();

	private final List<FacetCreator>						facetCreators			= new ArrayList<>();
	private final Map<FacetCreatorClassifier, FacetCreator>	facetCreatorsByTypes	= new HashMap<>();

	private int maxFacets;

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
		Function<String, FacetConfig> defaultFacetConfigProvider = getDefaultFacetConfigProvider(context);
		String defaultFacetType = defaultFacetConfigProvider.apply("").getType();

		maxFacets = context.config.getFacetConfiguration().getMaxFacets();

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

			if (FieldType.CATEGORY.equals(facetField.getType())) {
				if (!FacetType.HIERARCHICAL.name().equals(facetConfig.getType())) {
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
				if (facetConfig.getType().equals(FacetType.RANGE.name())) {
					if (facetField.isMasterLevel()) rangeFacets.put(facetField.getName(), facetConfig);
					if (facetField.isVariantLevel()) variantRangeFacets.put(facetField.getName(), facetConfig);
				}
				else {
					if (!facetConfig.getType().equals(FacetType.INTERVAL.name())) {
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
		CategoryFacetCreator categoryFacetCreator = new CategoryFacetCreator(hierarchicalFacets,
				FacetType.HIERARCHICAL.name().equals(defaultFacetType) ? defaultFacetConfigProvider : null);
		categoryFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(ignoredFields, FieldType.CATEGORY));
		facetCreators.add(categoryFacetCreator);
		facetCreatorsByTypes.put(FacetCreatorClassifier.hierarchicalFacet, categoryFacetCreator);

		Locale locale = context.config.getLocale();
		NestedFacetCreator masterTermFacetCreator = new TermFacetCreator(termFacets, FacetType.TERM.name().equals(defaultFacetType) ? defaultFacetConfigProvider : null, locale)
				.setMaxFacets(maxFacets);
		masterTermFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(ignoredFields, FieldType.STRING));
		facetCreators.add(masterTermFacetCreator);
		facetCreatorsByTypes.put(FacetCreatorClassifier.masterTermFacet, masterTermFacetCreator);

		NestedFacetCreator intervalFacetCreator = new IntervalFacetCreator(intervalFacets, FacetType.INTERVAL.name().equals(defaultFacetType) ? defaultFacetConfigProvider : null)
				.setMaxFacets(maxFacets);

		// range facets are only created for configured facets, so if there are
		// none, don't use that creator at all
		if (!rangeFacets.isEmpty()) {
			// TODO: FacetCreators that run on the same nested field, should be
			// grouped to use a single nested-aggregation for their aggregations
			NestedFacetCreator rangeFacetCreator = new RangeFacetCreator(rangeFacets, FacetType.RANGE.name().equals(defaultFacetType) ? defaultFacetConfigProvider : null)
					.setMaxFacets(maxFacets);
			rangeFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(ignoredFields, FieldType.NUMBER));
			facetCreators.add(rangeFacetCreator);
			facetCreatorsByTypes.put(FacetCreatorClassifier.masterRangeFacet, rangeFacetCreator);

			// exclude range facets from interval facet generation
			HashSet<String> excludeFields = new HashSet<>(rangeFacets.keySet());
			excludeFields.addAll(getNamesOfMatchingFields(ignoredFields, FieldType.NUMBER));
			intervalFacetCreator.setGeneralExcludedFields(excludeFields);
		}
		else {
			intervalFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(ignoredFields, FieldType.NUMBER));
		}

		facetCreators.add(intervalFacetCreator);
		facetCreatorsByTypes.put(FacetCreatorClassifier.masterIntervalFacet, intervalFacetCreator);

		List<FacetCreator> variantFacetCreators = new ArrayList<>();
		NestedFacetCreator variantTermFacetCreator = new TermFacetCreator(variantTermFacets, FacetType.TERM.name().equals(defaultFacetType) ? defaultFacetConfigProvider : null, locale)
				.setMaxFacets(maxFacets);
		variantTermFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(ignoredFields, FieldType.STRING));
		variantFacetCreators.add(variantTermFacetCreator);
		facetCreatorsByTypes.put(FacetCreatorClassifier.variantTermFacet, new VariantFacetCreator(Collections.singleton(variantTermFacetCreator)));

		NestedFacetCreator variantIntervalFacetCreator = new IntervalFacetCreator(variantIntervalFacets,
				FacetType.INTERVAL.name().equals(defaultFacetType) ? defaultFacetConfigProvider : null).setMaxFacets(maxFacets);
		variantIntervalFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(ignoredFields, FieldType.NUMBER));
		variantFacetCreators.add(variantIntervalFacetCreator);
		facetCreatorsByTypes.put(FacetCreatorClassifier.variantIntervalFacet, new VariantFacetCreator(Collections.singleton(variantIntervalFacetCreator)));

		if (!variantRangeFacets.isEmpty()) {
			NestedFacetCreator variantRangeFacetCreator = new RangeFacetCreator(variantRangeFacets,
					FacetType.RANGE.name().equals(defaultFacetType) ? defaultFacetConfigProvider : null).setMaxFacets(maxFacets);
			variantRangeFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(ignoredFields, FieldType.NUMBER));
			variantFacetCreators.add(variantRangeFacetCreator);
			facetCreatorsByTypes.put(FacetCreatorClassifier.variantRangeFacet, new VariantFacetCreator(Collections.singleton(variantRangeFacetCreator)));

			HashSet<String> excludeFields = new HashSet<>(variantRangeFacets.keySet());
			excludeFields.addAll(getNamesOfMatchingFields(ignoredFields, FieldType.NUMBER));
			variantIntervalFacetCreator.setGeneralExcludedFields(excludeFields);
		}
		else {
			variantIntervalFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(ignoredFields, FieldType.NUMBER));
		}

		// consolidated variant facet creator
		facetCreators.add(new VariantFacetCreator(variantFacetCreators));
	}

	private Function<String, FacetConfig> getDefaultFacetConfigProvider(SearchContext context) {
		FacetConfig defaultFacetConf = context.getConfig().getFacetConfiguration().getDefaultFacetConfiguration();
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
					.setValueOrder(defaultFacetConf.getValueOrder());
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
