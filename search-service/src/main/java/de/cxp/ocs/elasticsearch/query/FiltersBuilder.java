package de.cxp.ocs.elasticsearch.query;

import static de.cxp.ocs.config.FieldConstants.VARIANTS;
import static de.cxp.ocs.util.ESQueryUtils.mergeQueries;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import com.google.common.base.Functions;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.*;

public class FiltersBuilder {

	private final Set<String>	postFilterFacets	= new HashSet<>();
	private FieldConfigIndex	indexedFieldConfig;


	private static Map<Class<? extends InternalResultFilter>, InternalResultFilterAdapter<? extends InternalResultFilter>> filterAdapters = new HashMap<>(3);
	static {
		filterAdapters.put(NumberResultFilter.class, new NumberResultFilterAdapter());
		filterAdapters.put(TermResultFilter.class, new TermResultFilterAdapter());
		filterAdapters.put(PathResultFilter.class, new PathResultFilterAdapter());
	}

	public FiltersBuilder(SearchContext context) {
		for (FacetConfig facet : context.config.getFacetConfiguration().getFacets()) {
			if (facet.isMultiSelect() || facet.isShowUnselectedOptions()) postFilterFacets.add(facet.getSourceField());
		}
		indexedFieldConfig = context.getFieldConfigIndex();
	}

	public FilterContext buildFilterContext(List<InternalResultFilter> filters, List<InternalResultFilter> querqyFilters, boolean withFacets) {
		Map<String, InternalResultFilter> filtersByName = filters.stream().collect(
				Collectors.toMap(f -> f.getField().getName(), Functions.identity(),
						(a, b) -> {
							a.appendFilter(b);
							return a;
						}));

		if (filters.isEmpty() && querqyFilters.isEmpty()) return new FilterContext(filtersByName);

		Map<String, QueryBuilder> basicFilterQueries = new HashMap<>();
		Map<String, QueryBuilder> postFilterQueries = new HashMap<>();

		// collect filter queries on master and variant level
		buildFilterQueries(filters, basicFilterQueries, postFilterQueries, withFacets ? false : true);
		buildFilterQueries(querqyFilters, basicFilterQueries, postFilterQueries, true);

		MasterVariantQuery<QueryBuilder> postFilterQuery = buildFilters(postFilterQueries);
		QueryBuilder joinedPostFilters = mergeQueries(postFilterQuery.getMasterLevelQuery(), postFilterQuery
				.getVariantLevelQuery());

		return new FilterContext(
				filtersByName,
				Collections.unmodifiableMap(basicFilterQueries),
				Collections.unmodifiableMap(postFilterQueries),
				buildFilters(basicFilterQueries),
				joinedPostFilters,
				postFilterQuery.getVariantLevelQuery());
	}

	/**
	 *
	 * @param filters
	 * @param basicFilterQueries
	 * @param postFilterQueries
	 * @param addAllFiltersAsBasicFilters - if externally defined filters are used, they have to be treated as a basic filter
	 */
	private void buildFilterQueries(List<InternalResultFilter> filters, Map<String, QueryBuilder> basicFilterQueries,
		Map<String, QueryBuilder> postFilterQueries, boolean addAllFiltersAsBasicFilters) {
		for (InternalResultFilter filter : filters) {
			@SuppressWarnings("unchecked")
			InternalResultFilterAdapter<? super InternalResultFilter> filterAdapter = (InternalResultFilterAdapter<? super InternalResultFilter>) filterAdapters
					.get(filter.getClass());
			String fieldPrefix = filter.getFieldPrefix();

			boolean negationHandeled = false;
			QueryBuilder filterQuery = null;
			if (filter.getField().isMasterLevel()) {
				filterQuery = toFilterQuery(filter, fieldPrefix, filterAdapter);
			}

			if (filter.getField().isVariantLevel()) {
				fieldPrefix = VARIANTS + "." + fieldPrefix;
				if (filterQuery == null) {
					filterQuery = toFilterQuery(filter, fieldPrefix, filterAdapter);
				}
				else {
					// if a filter applies to both levels, then build a
					// SHOULD query (both field matches are wanted) for "include" filters
					// and a MUST_NOT query for negated/exclude filters.
					if (filter.isNegated()) {
						filterQuery = QueryBuilders.boolQuery()
								.mustNot(filterQuery)
								.mustNot(toFilterQuery(filter, fieldPrefix, filterAdapter));
						negationHandeled = true;
					}
					else {
						filterQuery = QueryBuilders.boolQuery()
							.should(filterQuery)
							.should(toFilterQuery(filter, fieldPrefix, filterAdapter));
					}
				}
			}

			if (filter.isNegated() && !negationHandeled) {
				filterQuery = QueryBuilders.boolQuery().mustNot(filterQuery);
				negationHandeled = true;
			}

			if (filter.isNegated() || addAllFiltersAsBasicFilters || isBasicQuery(filter.getField().getName())) {
				QueryBuilder conflictingFilter = basicFilterQueries.put(filter.getField().getName(), filterQuery);
				if (conflictingFilter != null) {
					basicFilterQueries.put(filter.getField().getName(), mergeQueries(conflictingFilter, filterQuery));
				}
			} else {
				postFilterQueries.put(filter.getField().getName(), filterQuery);
			}
		}
	}

	private boolean isBasicQuery(String fieldName) {
		return !postFilterFacets.contains(fieldName);
	}

	private MasterVariantQuery buildFilters(Map<String, QueryBuilder> filterQueries) {
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

		return new MasterVariantQuery(masterFilters, variantFilters, false, true);
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

	private boolean isVariantField(String field) {
		return indexedFieldConfig.getField(field).map(f -> f.isVariantLevel() && !f.isBothLevel()).orElse(false);
	}

}