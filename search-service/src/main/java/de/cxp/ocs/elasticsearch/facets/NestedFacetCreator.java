package de.cxp.ocs.elasticsearch.facets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
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

	protected abstract Optional<Facet> createFacet(Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter facetFilter, SearchQueryBuilder linkBuilder);

	@Override
	public AbstractAggregationBuilder<?> buildAggregation(FilterContext filters) {
		return buildAggregationWithNamesExcluded(filters, Collections.emptySet());
	}

	@Override
	public AbstractAggregationBuilder<?> buildAggregationWithNamesExcluded(FilterContext filterContext, Set<String> excludedNames) {
		String nestedPathPrefix = "";
		if (nestedFacetCorrector != null) nestedPathPrefix = nestedFacetCorrector.getNestedPathPrefix();
		nestedPathPrefix += getNestedPath();

		AggregationBuilder valueAggBuilder = getNestedValueAggregation(nestedPathPrefix);
		if (nestedFacetCorrector != null && correctedNestedDocumentCount()) nestedFacetCorrector.correctValueAggBuilder(valueAggBuilder);

		QueryBuilder facetNameFilter;
		if (excludedNames.isEmpty()) {
			facetNameFilter = QueryBuilders.matchAllQuery();
		}
		else {
			facetNameFilter = QueryBuilders.boolQuery().mustNot(QueryBuilders.termsQuery(nestedPathPrefix + ".name", excludedNames));
		}

		return AggregationBuilders.nested(uniqueAggregationName, nestedPathPrefix)
				.subAggregation(
						AggregationBuilders.filter(FILTERED_AGG, facetNameFilter)
								.subAggregation(
										AggregationBuilders.terms(FACET_NAMES_AGG)
												.field(nestedPathPrefix + ".name")
												.size(maxFacets)
												.subAggregation(valueAggBuilder)));
	}


	public QueryBuilder getFilterForTheGenericAggregations(FilterContext filters, String nestedFilterNamePath) {
		QueryBuilder allFilter;
		if (!filters.getPostFilterQueries().isEmpty() || onlyFetchAggregationsForConfiguredFacets() || !excludeFields.isEmpty()) {
			allFilter = QueryBuilders.boolQuery();
			// apply all post filters and
			// exclude facets that are currently active trough post filtering
			Set<String> allExcludeFields = new HashSet<>();
			if (!filters.getPostFilterQueries().isEmpty()) {
				((BoolQueryBuilder) allFilter).must(filters.getJoinedPostFilters());
				allExcludeFields.addAll(filters.getPostFilterQueries().keySet());
			}

			// if facet creator should only create the configured facets, filter
			// the names accordingly
			if (onlyFetchAggregationsForConfiguredFacets()) {
				((BoolQueryBuilder) allFilter).must(QueryBuilders.termsQuery(nestedFilterNamePath, facetConfigs.keySet()));
			}

			// if facet creator should exclude some facets, this is done here
			allExcludeFields.addAll(excludeFields);
			if (!allExcludeFields.isEmpty()) {
				((BoolQueryBuilder) allFilter).mustNot(QueryBuilders.termsQuery(nestedFilterNamePath, allExcludeFields));
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

			createFacet(facetNameBucket, facetConfig, facetFilter, linkBuilder).ifPresent(facets::add);
		}
		return facets;
	}


}
