package de.cxp.ocs.elasticsearch.query;

import static de.cxp.ocs.config.FieldConstants.VARIANTS;
import static de.cxp.ocs.util.ESQueryUtils.mergeQueries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.SearchConfiguration;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilterAdapter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilterAdapter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilterAdapter;

public class FiltersBuilder {

	private final Set<String>	postFilterFacets	= new HashSet<>();
	private FieldConfigIndex	indexedFieldConfig;

	private Map<String, QueryBuilder> filterQueries = new HashMap<>();

	private MasterVariantQuery	basicFilters;
	private QueryBuilder		postFilters;

	private static Map<Class<? extends InternalResultFilter>, InternalResultFilterAdapter<? extends InternalResultFilter>> filterAdapters = new HashMap<>(3);
	static {
		filterAdapters.put(NumberResultFilter.class, new NumberResultFilterAdapter());
		filterAdapters.put(TermResultFilter.class, new TermResultFilterAdapter());
	}

	public FiltersBuilder(SearchConfiguration searchConfig, List<InternalResultFilter> filters) {
		for (FacetConfig facet : searchConfig.getFacetConfiguration().getFacets()) {
			if (facet.isMultiSelect() || facet.isShowUnselectedOptions()) postFilterFacets.add(facet.getSourceField());
		}
		indexedFieldConfig = searchConfig.getIndexedFieldConfig();
		// TODO: replace stateful approach by "builder approach":
		// FiltersBuilder should be initialized once and transform the
		// InternalResultFilter list into a "FilterContext" or something like
		// that where it has all the ES specific filter queries
		prepareFilters(filters);
	}

	public QueryBuilder buildAggregationFilter(String facetName) {
		// TODO: build filter for multi-select facet aggregation
		// => (all filters combined without the specified one)
		return null;
	}

	public MasterVariantQuery buildBasicFilters() {
		if (basicFilters == null) {
			basicFilters = buildFilters(this::isBasicQuery);
		}
		return basicFilters;
	}

	public QueryBuilder buildPostFilters() {
		if (postFilters == null) {
			MasterVariantQuery separatedPostFilters = buildFilters(this::isPostFilterQuery);
			postFilters = mergeQueries(separatedPostFilters.getMasterLevelQuery(), separatedPostFilters
					.getVariantLevelQuery());
		}
		return postFilters;
	}

	private MasterVariantQuery buildFilters(Predicate<String> includeFields) {
		// collect filters and combine into master and variant filters
		QueryBuilder variantFilters = null;
		QueryBuilder masterFilters = null;
		for (Entry<String, QueryBuilder> nestedFieldFilters : filterQueries.entrySet()) {
			if (!includeFields.test(nestedFieldFilters.getKey())) continue;

			if (isVariantField(nestedFieldFilters.getKey())) {
				variantFilters = mergeQueries(variantFilters, nestedFieldFilters.getValue());
			}
			else {
				masterFilters = mergeQueries(masterFilters, nestedFieldFilters.getValue());
			}
		}

		return new MasterVariantQuery(masterFilters, variantFilters, false, true);
	}

	private boolean isBasicQuery(String fieldName) {
		return !postFilterFacets.contains(fieldName);
	}

	private boolean isPostFilterQuery(String fieldName) {
		return postFilterFacets.contains(fieldName);
	}

	private void prepareFilters(List<InternalResultFilter> filters) {
		if (filters.isEmpty()) return;

		// collect filter queries on master and variant level
		// TODO: very error prone code. Do this
		boolean buildVariantQueryAfterwards = false;
		for (InternalResultFilter filter : filters) {
			if (!buildVariantQueryAfterwards && isBasicQuery(filter.getField())) {
				buildVariantQueryAfterwards = true;
			}

			@SuppressWarnings("unchecked")
			InternalResultFilterAdapter<? super InternalResultFilter> filterAdapter = (InternalResultFilterAdapter<? super InternalResultFilter>) filterAdapters
					.get(filter.getClass());
			String fieldPrefix = filter.getFieldPrefix();

			QueryBuilder filterQuery = null;
			if (isMasterField(filter.getField())) {
				filterQuery = toFilterQuery(filter, fieldPrefix, filterAdapter);
			}

			if (isVariantField(filter.getField())) {
				fieldPrefix = VARIANTS + "." + fieldPrefix;
				if (filterQuery == null) {
					filterQuery = toFilterQuery(filter, fieldPrefix, filterAdapter);
				}
				else {
					// if a filter applies to both levels, then build a
					// boolean-should query (both field matches are wanted)
					filterQuery = QueryBuilders.boolQuery()
							.should(filterQuery)
							.should(toFilterQuery(filter, fieldPrefix, filterAdapter));
				}
			}

			filterQueries.put(filter.getField(), filterQuery);
		}

		// call buildBasicFilters() to generate variant query
		if (buildVariantQueryAfterwards) {
			buildBasicFilters();
		}
	}

	private QueryBuilder toFilterQuery(InternalResultFilter filter, String fieldPrefix, InternalResultFilterAdapter<? super InternalResultFilter> filterAdapter) {
		if (filter.isNestedFilter()) {
			return QueryBuilders.nestedQuery(
					fieldPrefix,
					filterAdapter.getAsQuery(fieldPrefix + ".", filter),
					ScoreMode.None);
		}
		else {
			return filterAdapter.getAsQuery(fieldPrefix, filter);
		}
	}

	private boolean isVariantField(String field) {
		return indexedFieldConfig.getField(field).map(Field::isVariantLevel).orElse(false);
	}

	private boolean isMasterField(String field) {
		return indexedFieldConfig.getField(field).map(Field::isMasterLevel).orElse(false);
	}

}
