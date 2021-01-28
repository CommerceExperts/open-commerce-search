package de.cxp.ocs.elasticsearch.facets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregator.KeyedFilter;

import de.cxp.ocs.elasticsearch.query.filter.FilterContext;

public interface NestedFacetCreator extends FacetCreator {

	NestedFacetCreator setNestedFacetCorrector(NestedFacetCountCorrector c);

	static final String	FILTERED_AGG			= "_filtered";
	static final String	ALL_BUT_FILTER_PREFIX	= "_all_but_";
	static final String	ALL_FILTER_NAME			= "_all";

	/**
	 * <p>
	 * General utility method for FacetCreators to create aggregation filters
	 * for a proper postFilter handling. At the result one keyed filter will be
	 * named with the ALL_FILTER_NAME. For every post-filter a separate filter
	 * is created with the name of the filters prefixed by
	 * ALL_BUT_FILTER_PREFIX.
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
	 * 
	 * 
	 * @param filters
	 *        the container that holds the filter queries
	 * @param nestedFilterNamePath
	 *        The nested path that should be used to exclude the post-filter
	 *        names.
	 * @return
	 *         list of keyed filters
	 */
	static List<KeyedFilter> getAggregationFilters(FilterContext filters, String nestedFilterNamePath) {
		// for facets that are currently filtered
		List<KeyedFilter> facetFilters = new ArrayList<>();
		Map<String, QueryBuilder> postFilters = filters.getPostFilterQueries();
		for (final Entry<String, QueryBuilder> postFilter : postFilters.entrySet()) {
			// create a filter that filters on the name of the post filter and
			// all the other post filters
			facetFilters.add(new KeyedFilter(ALL_BUT_FILTER_PREFIX + postFilter.getKey(), QueryBuilders.boolQuery()
					.must(FilterContext.allButOne(postFilter.getKey(), postFilters))
					.must(QueryBuilders.termQuery(nestedFilterNamePath, postFilter.getKey()))));
		}

		// always filter for all post filters
		// also exclude the facets that handled separately
		// (or match all if there are no post filters)
		QueryBuilder allFilter = filters.allWithPostFilterNamesExcluded(nestedFilterNamePath);

		facetFilters.add(new KeyedFilter(ALL_FILTER_NAME, allFilter));
		return facetFilters;
	}

}
