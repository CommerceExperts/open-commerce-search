package de.cxp.ocs.elasticsearch.query;

import static de.cxp.ocs.config.FieldConstants.VARIANTS;
import static de.cxp.ocs.util.ESQueryUtils.mergeQueries;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.util.ESQueryUtils;
import de.cxp.ocs.util.InternalSearchParams;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import com.google.common.base.Functions;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.*;

public class FiltersBuilder {

	private final Set<String>      postFilterFacets = new HashSet<>();
	private final FieldConfigIndex indexedFieldConfig;

	private static final Map<Class<? extends InternalResultFilter>, InternalResultFilterAdapter<? extends InternalResultFilter>> filterAdapters = new HashMap<>(3);

	static {
		filterAdapters.put(NumberResultFilter.class, new NumberResultFilterAdapter());
		filterAdapters.put(TermResultFilter.class, new TermResultFilterAdapter());
		filterAdapters.put(PathResultFilter.class, new PathResultFilterAdapter());
	}

	public FiltersBuilder(SearchContext context) {
		for (FacetConfig facet : context.config.getFacetConfiguration().getFacets()) {
			if ((facet.isMultiSelect() || facet.isShowUnselectedOptions()) && !facet.isFilterSensitive()) postFilterFacets.add(facet.getSourceField());
		}
		indexedFieldConfig = context.getFieldConfigIndex();
	}

	public FilterContext buildFilterContext(InternalSearchParams parameters) {
		List<InternalResultFilter> filters = parameters.filters;
		List<InternalResultFilter> querqyFilters = parameters.inducedFilters;
		boolean withFacets = parameters.withFacets;
		if (filters.isEmpty() && querqyFilters.isEmpty()) return new FilterContext(Collections.emptyMap(), parameters.customParams);

		Map<String, InternalResultFilter> filtersByName = filters.stream().collect(
				Collectors.toMap(f -> f.getField().getName(), Functions.identity(),
						(a, b) -> {
							a.appendFilter(b);
							return a;
						}));

		// collect filter queries on master and variant level
		FilterCollector filterCollector = new FilterCollector();
		buildFilterQueries(filters, filterCollector, !withFacets);
		buildFilterQueries(querqyFilters, filterCollector, true);

		QueryBuilder joinedBasicFilters = filterCollector.buildJoinedBaseFilter();
		QueryBuilder joinedPostFilters = filterCollector.buildJoinedPostFilter();
		QueryBuilder innerHitsFilter = mergeQueries(filterCollector.variantInnerHitsFilters.values());

		return new FilterContext(
				filtersByName,
				Collections.unmodifiableMap(filterCollector.postFilterQueries),
				joinedBasicFilters,
				joinedPostFilters,
				innerHitsFilter,
				parameters.customParams);
	}

	private void buildFilterQueries(List<InternalResultFilter> filters, FilterCollector filterCollector, boolean addAllFiltersAsBasicFilters) {
		for (InternalResultFilter filter : filters) {
			@SuppressWarnings("unchecked")
			InternalResultFilterAdapter<? super InternalResultFilter> filterAdapter = (InternalResultFilterAdapter<? super InternalResultFilter>) filterAdapters
					.get(filter.getClass());
			String fieldPrefix = filter.getFieldPrefix();

			QueryBuilder masterFilterQuery = null;
			if (filter.getField().isMasterLevel()) {
				masterFilterQuery = toFilterQuery(filter, fieldPrefix, filterAdapter);
			}

			QueryBuilder variantFilterQuery = null;
			if (filter.getField().isVariantLevel()) {
				fieldPrefix = VARIANTS + "." + fieldPrefix;
				variantFilterQuery = toFilterQuery(filter, fieldPrefix, filterAdapter);
				filterCollector.addVariantInnerHitsFilter(filter, filter.isNegated() ? QueryBuilders.boolQuery().mustNot(variantFilterQuery) : variantFilterQuery);
			}

			if (masterFilterQuery != null && variantFilterQuery != null) {
				// filter applies to both levels
				if (filter.isNegated() || addAllFiltersAsBasicFilters || isBasicQuery(filter.getField().getName())) {
					filterCollector.addDualBasicFilterQuery(filter, masterFilterQuery, variantFilterQuery);
				}
				else {
					filterCollector.addDualPostFilterQuery(filter, masterFilterQuery, variantFilterQuery);
				}
			}
			else {
				var singleFilter = variantFilterQuery == null ? masterFilterQuery : variantFilterQuery;
				if (filter.isNegated() || addAllFiltersAsBasicFilters || isBasicQuery(filter.getField().getName())) {
					filterCollector.addBasicFilterQuery(filter, singleFilter);
				}
				else {
					filterCollector.addPostFilterQuery(filter, singleFilter);
				}
			}
		}
	}

	private QueryBuilder toFilterQuery(InternalResultFilter filter, String fieldPrefix, InternalResultFilterAdapter<? super InternalResultFilter> filterAdapter) {
		QueryBuilder filterQuery;
		if (filter.isNestedFilter()) {
			filterQuery = QueryBuilders.nestedQuery(
					fieldPrefix,
					filterAdapter.getAsQuery(fieldPrefix + ".", filter),
					ScoreMode.None);
		}
		else {
			filterQuery = filterAdapter.getAsQuery(fieldPrefix + ".", filter);
		}
		return filterQuery;
	}

	private boolean isBasicQuery(String fieldName) {
		return !postFilterFacets.contains(fieldName);
	}

	private class FilterCollector {

		final Map<String, QueryBuilder> basicFilterQueries       = new HashMap<>();
		final BoolQueryBuilder          dualMasterFilterQueries  = QueryBuilders.boolQuery();
		final BoolQueryBuilder          dualVariantFilterQueries = QueryBuilders.boolQuery();

		final Map<String, QueryBuilder> postFilterQueries        = new HashMap<>();
		final Map<String, QueryBuilder> postFilterMasterQueries  = new HashMap<>();
		final Map<String, QueryBuilder> postFilterVariantQueries = new HashMap<>();

		final Map<String, QueryBuilder> variantInnerHitsFilters = new HashMap<>();

		void addBasicFilterQuery(InternalResultFilter filter, QueryBuilder filterQuery) {
			if (filter.isNegated()) filterQuery = QueryBuilders.boolQuery().mustNot(filterQuery);
			addFilterQuery(basicFilterQueries, filter.getField().getName(), filterQuery);
		}

		void addDualBasicFilterQuery(InternalResultFilter filter, QueryBuilder masterFilter, QueryBuilder variantFilter) {
			if (filter.isNegated()) {
				dualMasterFilterQueries.mustNot(masterFilter);
				dualVariantFilterQueries.mustNot(variantFilter);
			}
			else {
				dualMasterFilterQueries.must(masterFilter);
				dualVariantFilterQueries.must(variantFilter);
			}
		}

		void addPostFilterQuery(InternalResultFilter filter, QueryBuilder filterQuery) {
			// no need to handle negated filter - those are always applied as basic filters
			addFilterQuery(postFilterQueries, filter.getField().getName(), filterQuery);
		}

		void addDualPostFilterQuery(InternalResultFilter filter, QueryBuilder masterFilter, QueryBuilder variantFilter) {
			// no need to handle negated filter - those are always applied as basic filters
			addFilterQuery(postFilterMasterQueries, filter.getField().getName(), masterFilter);
			addFilterQuery(postFilterVariantQueries, filter.getField().getName(), variantFilter);
		}

		void addVariantInnerHitsFilter(InternalResultFilter filter, QueryBuilder filterQuery) {
			addFilterQuery(variantInnerHitsFilters, filter.getField().getName(), filterQuery);
		}

		static void addFilterQuery(Map<String, QueryBuilder> targetMap, String fieldName, QueryBuilder filterQuery) {
			QueryBuilder conflictingFilter = targetMap.put(fieldName, filterQuery);
			if (conflictingFilter != null) {
				targetMap.put(fieldName, mergeQueries(conflictingFilter, filterQuery));
			}
		}

		QueryBuilder buildJoinedBaseFilter() {
			QueryBuilder joinedFilter = joinFilters(basicFilterQueries);

			// both levels have same amount of filters. They are attached as xor filter (a should clause with min-should-match = 1)
			if (dualMasterFilterQueries.hasClauses()) {
				BoolQueryBuilder dualFilterQuery = QueryBuilders.boolQuery()
						.should(dualMasterFilterQueries)
						.should(QueryBuilders.nestedQuery(VARIANTS, dualVariantFilterQueries, ScoreMode.None))
						.minimumShouldMatch(1);
				joinedFilter = joinedFilter == null ? dualFilterQuery : ESQueryUtils.mapToBoolQueryBuilder(joinedFilter).filter(dualFilterQuery);
			}
			return joinedFilter;
		}

		QueryBuilder buildJoinedPostFilter() {
			QueryBuilder joinedPostFilter = joinFilters(postFilterQueries);

			// both levels have same amount of filters. They are attached as xor filter (a should clause with min-should-match = 1)
			if (!postFilterVariantQueries.isEmpty()) {
				// add variant filters to post-filter, because this is how to make sure, that a single variant meets all criterion
				// otherwise it would match a product that has one variant with the non-post-filters and another variant with the post-filters
				variantInnerHitsFilters.forEach((name, filterQuery) -> {
					if (!postFilterVariantQueries.containsKey(name)) {
						postFilterVariantQueries.put(name, filterQuery);
					}
				});
				BoolQueryBuilder dualFilterQuery = QueryBuilders.boolQuery()
						.should(mergeQueries(postFilterMasterQueries.values()))
						.should(QueryBuilders.nestedQuery(VARIANTS, mergeQueries(postFilterVariantQueries.values()), ScoreMode.None))
						.minimumShouldMatch(1);
				joinedPostFilter = joinedPostFilter == null ? dualFilterQuery : ESQueryUtils.mapToBoolQueryBuilder(joinedPostFilter).filter(dualFilterQuery);
			}
			return joinedPostFilter;
		}

		QueryBuilder joinFilters(Map<String, QueryBuilder> filterQueries) {
			// collect filters and combine into master and variant filters
			QueryBuilder variantFilters = null;
			QueryBuilder masterFilters = null;
			for (Entry<String, QueryBuilder> nestedFieldFilters : filterQueries.entrySet()) {
				if (isVariantField(nestedFieldFilters.getKey())) {
					variantFilters = mergeQueries(variantFilters, nestedFieldFilters.getValue());
				}
				else {
					masterFilters = mergeQueries(masterFilters, nestedFieldFilters.getValue());
				}
			}
			if (variantFilters != null) variantFilters = QueryBuilders.nestedQuery(VARIANTS, variantFilters, ScoreMode.None);

			return mergeQueries(masterFilters, variantFilters);
		}

		private boolean isVariantField(String field) {
			return indexedFieldConfig.getField(field).map(Field::isVariantLevel).orElse(false);
		}

	}

}
