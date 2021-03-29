package de.cxp.ocs.elasticsearch.facets;

import static de.cxp.ocs.elasticsearch.facets.FacetFactory.getLabel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.lucene.search.join.ScoreMode;
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

		final static FacetCreatorClassifier hierarchicalFacet = new FacetCreatorClassifier(false, FacetType.hierarchical.name());

		final static FacetCreatorClassifier	masterIntervalFacet	= new FacetCreatorClassifier(false, FacetType.interval.name());
		final static FacetCreatorClassifier	masterRangeFacet	= new FacetCreatorClassifier(false, FacetType.range.name());
		final static FacetCreatorClassifier	masterTermFacet		= new FacetCreatorClassifier(false, FacetType.term.name());

		final static FacetCreatorClassifier	variantIntervalFacet	= new FacetCreatorClassifier(true, FacetType.interval.name());
		final static FacetCreatorClassifier	variantRangeFacet		= new FacetCreatorClassifier(true, FacetType.range.name());
		final static FacetCreatorClassifier	variantTermFacet		= new FacetCreatorClassifier(true, FacetType.term.name());

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

			if (FieldType.category.equals(facetField.getType())) {
				if (!FacetType.hierarchical.name().equals(facetConfig.getType())) {
					log.warn("facet {} based on *category* field {} was configured as {} facet, but only 'hierarchical' type is supported",
							facetConfig.getLabel(), facetField.getName(), facetConfig.getType());
					facetConfig.setType(FacetType.hierarchical.name());
				}
				if (!facetField.isVariantLevel()) {
					hierarchicalFacets.put(facetField.getName(), facetConfig);
				}
				else {
					log.warn("*category* facet {} based on field {} does not work, as it's a variant field",
							facetConfig.getLabel(), facetField.getName());
				}
			}
			else if (FieldType.number.equals(facetField.getType())) {
				if (facetConfig.getType().equals(FacetType.range.name())) {
					if (facetField.isMasterLevel()) rangeFacets.put(facetField.getName(), facetConfig);
					if (facetField.isVariantLevel()) variantRangeFacets.put(facetField.getName(), facetConfig);
				}
				else {
					if (!facetConfig.getType().equals(FacetType.interval.name())) {
						log.warn("facet {} based on *number* field {} was configured as {} facet, but only 'interval' or 'range' type is supported."
								+ " To create a 'term' facet on numeric data, the according field has to be indexed as *string* field.",
								facetConfig.getLabel(), facetField.getName(), facetConfig.getType());
					}
					if (facetField.isMasterLevel()) intervalFacets.put(facetField.getName(), facetConfig);
					if (facetField.isVariantLevel()) variantIntervalFacets.put(facetField.getName(), facetConfig);
				}
			}
			else {
				if (!FacetType.term.name().equals(facetConfig.getType())) {
					log.warn("facet {} based on *{}* field {} was configured as {} facet, but only 'term' type is supported",
							facetConfig.getLabel(), facetField.getType(), facetField.getName(), facetConfig.getType());
				}
				if (facetField.isVariantLevel()) variantTermFacets.put(facetField.getName(), facetConfig);
				if (facetField.isMasterLevel()) termFacets.put(facetField.getName(), facetConfig);
			}
		}

		// build all generic facet creators passing the specific configs to it
		CategoryFacetCreator categoryFacetCreator = new CategoryFacetCreator(hierarchicalFacets);
		facetCreators.add(categoryFacetCreator);
		facetCreatorsByTypes.put(FacetCreatorClassifier.hierarchicalFacet, categoryFacetCreator);

		NestedFacetCreator masterTermFacetCreator = new TermFacetCreator(termFacets).setMaxFacets(maxFacets);
		facetCreators.add(masterTermFacetCreator);
		facetCreatorsByTypes.put(FacetCreatorClassifier.masterTermFacet, masterTermFacetCreator);

		NestedFacetCreator intervalFacetCreator = new IntervalFacetCreator(intervalFacets).setMaxFacets(maxFacets);

		// range facets are only created for configured facets, so if there are
		// none, don't use that creator at all
		if (!rangeFacets.isEmpty()) {
			// TODO: FacetCreators that run on the same nested field, should be
			// grouped to use a single nested-aggregation for their aggregations
			NestedFacetCreator rangeFacetCreator = new RangeFacetCreator(rangeFacets).setMaxFacets(maxFacets);
			facetCreators.add(rangeFacetCreator);
			facetCreatorsByTypes.put(FacetCreatorClassifier.masterRangeFacet, rangeFacetCreator);

			// exclude range facets from interval facet generation
			intervalFacetCreator.setGeneralExcludedFields(rangeFacets.keySet());
		}

		facetCreators.add(intervalFacetCreator);
		facetCreatorsByTypes.put(FacetCreatorClassifier.masterIntervalFacet, intervalFacetCreator);

		List<FacetCreator> variantFacetCreators = new ArrayList<>();
		NestedFacetCreator variantTermFacetCreator = new TermFacetCreator(variantTermFacets).setMaxFacets(maxFacets);
		variantFacetCreators.add(variantTermFacetCreator);
		facetCreatorsByTypes.put(FacetCreatorClassifier.variantTermFacet, new VariantFacetCreator(Collections.singleton(variantTermFacetCreator)));

		NestedFacetCreator variantIntervalFacetCreator = new IntervalFacetCreator(variantIntervalFacets).setMaxFacets(maxFacets);
		variantFacetCreators.add(variantIntervalFacetCreator);
		facetCreatorsByTypes.put(FacetCreatorClassifier.variantIntervalFacet, new VariantFacetCreator(Collections.singleton(variantIntervalFacetCreator)));

		if (!variantRangeFacets.isEmpty()) {
			NestedFacetCreator variantRangeFacetCreator = new RangeFacetCreator(variantRangeFacets).setMaxFacets(maxFacets);
			variantFacetCreators.add(variantRangeFacetCreator);
			facetCreatorsByTypes.put(FacetCreatorClassifier.variantRangeFacet, new VariantFacetCreator(Collections.singleton(variantRangeFacetCreator)));

			variantIntervalFacetCreator.setGeneralExcludedFields(variantRangeFacets.keySet());
		}
		// consolidated variant facet creator
		facetCreators.add(new VariantFacetCreator(variantFacetCreators));
	}

	private FacetType getDefaultFacetType(FieldType type) {
		switch (type) {
			case number:
				return FacetType.interval;
			case category:
				return FacetType.hierarchical;
			case combi:
			case id:
			case string:
			default:
				return FacetType.term;
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
				aggregators.add(creator.buildAggregation(filterContext));
			}
		}
		else {
			Map<String, QueryBuilder> postFilters = filterContext.getPostFilterQueries();
			for (String postFilterName : postFilters.keySet()) {
				InternalResultFilter internalFilter = filterContext.getInternalFilters().get(postFilterName);
				QueryBuilder exclusiveFilterQuery = getExclusivePostFilterQuery(postFilterName, internalFilter, postFilters);

				FilterAggregationBuilder filterAgg = AggregationBuilders.filter(EXCLUSIVE_AGG_PREFIX + postFilterName, exclusiveFilterQuery);

				getResponsibleFacetCreators(internalFilter)
						.forEach(facetCreator -> filterAgg.subAggregation(facetCreator.buildAggregation(filterContext)));

				aggregators.add(filterAgg);
			}

			// create a filter for all post filters and add all aggregations
			// that are not specialized for all the post filters
			FilterAggregationBuilder fullFilteredAgg = AggregationBuilders.filter(FILTERED_AGG_NAME, filterContext.getJoinedPostFilters());
			for (FacetCreator creator : facetCreators) {
				fullFilteredAgg.subAggregation(creator.buildAggregationWithNamesExcluded(filterContext, filterContext.getPostFilterQueries().keySet()));
			}
			aggregators.add(fullFilteredAgg);
		}

		return aggregators;
	}

	private QueryBuilder getExclusivePostFilterQuery(String postFilterName, InternalResultFilter internalFilter, Map<String, QueryBuilder> postFilters) {
		String nestedPrefix = internalFilter.getFieldPrefix();
		String nestedFilterNamePath = nestedPrefix + ".name";

		// create a filter that filters on the name of the post filter
		QueryBuilder nameFilter = QueryBuilders.nestedQuery(
				nestedPrefix,
				QueryBuilders.termQuery(nestedFilterNamePath, postFilterName),
				ScoreMode.None);

		// and combines that with all other post filters
		QueryBuilder finalAggFilter = FilterContext.joinAllButOne(postFilterName, postFilters)
				// .map(q -> (QueryBuilder)
				// ESQueryUtils.mapToBoolQueryBuilder(q).must(nameFilter))
				.orElse(nameFilter);
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
				int compare = Byte.compare(FacetFactory.getOrder(o1), FacetFactory.getOrder(o2));
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
			facetsFromUnfilteredAggregations(filteredAggregation.getAggregations(), filterContext, linkBuilder)
					.forEach(f -> facets.put(getLabel(f), f));
		}

		for (String postFilterName : filterContext.getPostFilterQueries().keySet()) {
			Filter exclusiveAgg = aggregations.get(EXCLUSIVE_AGG_PREFIX + postFilterName);
			if (exclusiveAgg != null && exclusiveAgg.getDocCount() > 0L) {
				List<FacetCreator> matchingFacetCreators = getResponsibleFacetCreators(filterContext.getInternalFilters().get(postFilterName));
				for (FacetCreator fc : matchingFacetCreators) {
					fc.createFacets(exclusiveAgg.getAggregations(), filterContext, linkBuilder)
							.forEach(f -> facets.put(getLabel(f), f));
				}
			}
		}

		return new ArrayList<>(facets.values());
	}

	private List<Facet> facetsFromUnfilteredAggregations(Aggregations aggregations, FilterContext filterContext, SearchQueryBuilder linkBuilder) {
		List<Facet> facets = new ArrayList<>();
		Set<String> duplicateFacets = new HashSet<>();

		// get filtered facets
		Set<String> appliedFilters = filterContext.getInternalFilters().keySet();

		for (FacetCreator fc : facetCreators) {
			Collection<Facet> createdFacets = fc.createFacets(aggregations, filterContext, linkBuilder);
			for (Facet f : createdFacets) {
				// skip facets with the identical name
				if (!duplicateFacets.add(getLabel(f))) {
					log.warn("duplicate facet with label " + getLabel(f));
					continue;
				}
				if (appliedFilters.contains(f.getFieldName())) {
					f.setFiltered(true);
				}

				FacetConfig facetConfig = facetsBySourceField.get(f.getFieldName());
				if (facetConfig != null && facetConfig.isExcludeFromFacetLimit()) {
					f.meta.put(IS_MANDATORY_META_KEY, true);
				}
				facets.add(f);
			}
		}
		return facets;
	}

}
