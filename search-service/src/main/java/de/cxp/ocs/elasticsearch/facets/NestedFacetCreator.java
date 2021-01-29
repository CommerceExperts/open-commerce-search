package de.cxp.ocs.elasticsearch.facets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregator.KeyedFilter;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilters;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.util.SearchQueryBuilder;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public abstract class NestedFacetCreator implements FacetCreator {

	static final String	FILTERED_AGG			= "_filtered";
	static final String	ALL_BUT_FILTER_PREFIX	= "_all_but_";
	static final String	ALL_FILTER_NAME			= "_all";

	static final String	FACET_NAMES_AGG		= "_names";
	static final String	FACET_VALUES_AGG	= "_values";

	@Setter
	private int maxFacets = 2;

	@Setter
	protected NestedFacetCountCorrector nestedFacetCorrector = null;

	@Setter
	private String uniqueAggregationName = this.getClass().getSimpleName() + "Aggregation";

	private final Map<String, FacetConfig> facetConfigs;

	@Setter
	@NonNull
	private Set<String> excludeFields = Collections.emptySet();

	public NestedFacetCreator(Map<String, FacetConfig> facetConfigs) {
		this.facetConfigs = facetConfigs;
	}

	protected abstract String getNestedPath();

	protected abstract AggregationBuilder getNestedValueAggregation(String nestedPathPrefix);

	protected abstract boolean onlyFetchAggregationsForConfiguredFacets();

	protected abstract boolean correctedNestedDocumentCount();

	protected abstract boolean isMatchingFilterType(InternalResultFilter internalResultFilter);

	protected abstract Facet createFacet(Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter facetFilter, SearchQueryBuilder linkBuilder);

	@Override
	public AbstractAggregationBuilder<?> buildAggregation(FilterContext filters) {
		String nestedPathPrefix = "";
		if (nestedFacetCorrector != null) nestedPathPrefix = nestedFacetCorrector.getNestedPathPrefix();
		nestedPathPrefix += getNestedPath();

		AggregationBuilder valueAggBuilder = getNestedValueAggregation(nestedPathPrefix);
		if (nestedFacetCorrector != null && correctedNestedDocumentCount()) nestedFacetCorrector.correctValueAggBuilder(valueAggBuilder);

		List<KeyedFilter> facetFilters = getAggregationFilters(filters, nestedPathPrefix + ".name");

		return AggregationBuilders.nested(uniqueAggregationName, nestedPathPrefix)
				.subAggregation(
						AggregationBuilders.filters(FILTERED_AGG, facetFilters.toArray(new KeyedFilter[0]))
								.subAggregation(
										AggregationBuilders.terms(FACET_NAMES_AGG)
												.field(nestedPathPrefix + ".name")
												.size(maxFacets)
												.subAggregation(valueAggBuilder)));
	}

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
	private List<KeyedFilter> getAggregationFilters(FilterContext filters, String nestedFilterNamePath) {
		// for facets that are currently filtered
		List<KeyedFilter> facetFilters = new ArrayList<>();
		Map<String, QueryBuilder> postFilters = filters.getPostFilterQueries();
		for (final Entry<String, QueryBuilder> postFilter : postFilters.entrySet()) {
			if (excludeFields.contains(postFilter.getKey())
					|| !isMatchingFilterType(filters.getInternalFilters().get(postFilter.getKey())))
				continue;

			// create a filter that filters on the name of the post filter and
			// all the other post filters
			facetFilters.add(new KeyedFilter(ALL_BUT_FILTER_PREFIX + postFilter.getKey(), QueryBuilders.boolQuery()
					.must(FilterContext.joinAllButOne(postFilter.getKey(), postFilters))
					.must(QueryBuilders.termQuery(nestedFilterNamePath, postFilter.getKey()))));
		}

		// always filter for all post filters
		// also exclude the facets that handled separately
		// (or match all if there are no post filters)
		QueryBuilder allFilter = getFilterForTheGenericAggregations(filters, nestedFilterNamePath);

		facetFilters.add(new KeyedFilter(ALL_FILTER_NAME, allFilter));
		return facetFilters;
	}


	public QueryBuilder getFilterForTheGenericAggregations(FilterContext filters, String nestedFilterNamePath) {
		QueryBuilder allFilter;
		if (!filters.getPostFilterQueries().isEmpty() || onlyFetchAggregationsForConfiguredFacets() || !excludeFields.isEmpty()) {
			allFilter = QueryBuilders.boolQuery();
			// exclude facets that are currently active trough post filtering
			if (!filters.getPostFilterQueries().isEmpty()) {
				((BoolQueryBuilder) allFilter).must(filters.getJoinedPostFilters());
			}

			// if facet creator should only create the configured facets, filter
			// the names accordingly
			if (onlyFetchAggregationsForConfiguredFacets()) {
				((BoolQueryBuilder) allFilter).must(QueryBuilders.termsQuery(nestedFilterNamePath, facetConfigs.keySet()));
			}

			// if facet creator should exclude some facets, this is done here
			if (!excludeFields.isEmpty()) {
				((BoolQueryBuilder) allFilter).mustNot(QueryBuilders.termsQuery(nestedFilterNamePath, excludeFields));
			}
		}
		else {
			allFilter = QueryBuilders.matchAllQuery();
		}
		return allFilter;
	}

	@Override
	public Collection<Facet> createFacets(Aggregations aggResult, FilterContext filterContext, SearchQueryBuilder linkBuilder) {
		ParsedFilters filtersAgg = ((Nested) aggResult.get(uniqueAggregationName)).getAggregations().get(FILTERED_AGG);
		List<Facet> extractedFacets = new ArrayList<>();
		for (org.elasticsearch.search.aggregations.bucket.filter.Filters.Bucket filterBucket : filtersAgg.getBuckets()) {
			Terms facetNamesAggregation = filterBucket.getAggregations().get(FACET_NAMES_AGG);
			extractedFacets.addAll(extractFacets(facetNamesAggregation, filterContext, linkBuilder));
		}

		return extractedFacets;
	}

	protected List<Facet> extractFacets(Terms facetNames, FilterContext filterContext, SearchQueryBuilder linkBuilder) {
		List<Facet> facets = new ArrayList<>();
		for (Terms.Bucket facetNameBucket : facetNames.getBuckets()) {
			String facetName = facetNameBucket.getKeyAsString();

			// XXX: using a dynamic string as source field might be a bad
			// idea for link creation
			// either log warnings when indexing such attributes or map them to
			// some internal URL friendly name
			FacetConfig facetConfig = facetConfigs.get(facetName);
			if (facetConfig == null) facetConfig = new FacetConfig(facetName, facetName);

			InternalResultFilter facetFilter = filterContext.getInternalFilters().get(facetName);

			facets.add(createFacet(facetNameBucket, facetConfig, facetFilter, linkBuilder));
		}
		return facets;
	}


}
