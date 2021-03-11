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
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
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

	static final String	FILTERED_AGG		= "_filtered";
	static final String	FACET_NAMES_AGG		= "_names";
	static final String	FACET_VALUES_AGG	= "_values";
	static final String	FACET_IDS_AGG		= "_ids";

	@Setter
	private int maxFacets = 2;

	@Setter
	protected NestedFacetCountCorrector nestedFacetCorrector = new NestedFacetCountCorrector("");

	@Setter
	private String uniqueAggregationName = this.getClass().getSimpleName() + "Aggregation";

	private final Map<String, FacetConfig> facetConfigs;

	@Setter
	@NonNull
	private Set<String> generalExcludedFields = Collections.emptySet();

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

		QueryBuilder facetNameFilter = getNameFilter(nestedPathPrefix + ".name", excludedNames);

		return AggregationBuilders.nested(uniqueAggregationName, nestedPathPrefix)
				.subAggregation(
						AggregationBuilders.filter(FILTERED_AGG, facetNameFilter)
								.subAggregation(
										AggregationBuilders.terms(FACET_NAMES_AGG)
												.field(nestedPathPrefix + ".name")
												.size(maxFacets)
												.subAggregation(valueAggBuilder)));
	}


	public QueryBuilder getNameFilter(String nestedFilterNamePath, Set<String> excludedNames) {
		QueryBuilder allFilter;
		if (!excludedNames.isEmpty() || onlyFetchAggregationsForConfiguredFacets() || !generalExcludedFields.isEmpty()) {
			allFilter = QueryBuilders.boolQuery();
			// apply all post filters and
			// exclude facets that are currently active trough post filtering
			Set<String> allExcludeFields = new HashSet<>();
			if (!excludedNames.isEmpty()) {
				allExcludeFields.addAll(excludedNames);
			}

			// if facet creator should only create the configured facets, filter
			// the names accordingly
			if (onlyFetchAggregationsForConfiguredFacets()) {
				((BoolQueryBuilder) allFilter).must(QueryBuilders.termsQuery(nestedFilterNamePath, facetConfigs.keySet()));
			}

			// if facet creator should exclude some facets, this is done here
			allExcludeFields.addAll(generalExcludedFields);
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
		ParsedFilter filtersAgg = ((Nested) aggResult.get(uniqueAggregationName)).getAggregations().get(FILTERED_AGG);
		if (filtersAgg == null) return Collections.emptyList();

		Terms facetNamesAggregation = filtersAgg.getAggregations().get(FACET_NAMES_AGG);
		List<Facet> extractedFacets = extractFacets(facetNamesAggregation, filterContext, linkBuilder);

		return extractedFacets;
	}

	protected List<Facet> extractFacets(Terms facetNames, FilterContext filterContext, SearchQueryBuilder linkBuilder) {
		List<Facet> facets = new ArrayList<>();
		for (Terms.Bucket facetNameBucket : facetNames.getBuckets()) {
			String facetName = facetNameBucket.getKeyAsString();

			FacetConfig facetConfig = facetConfigs.get(facetName);
			if (facetConfig == null) facetConfig = new FacetConfig(facetName, facetName);

			InternalResultFilter facetFilter = filterContext.getInternalFilters().get(facetName);

			createFacet(facetNameBucket, facetConfig, facetFilter, linkBuilder).ifPresent(facets::add);
		}
		return facets;
	}


}
