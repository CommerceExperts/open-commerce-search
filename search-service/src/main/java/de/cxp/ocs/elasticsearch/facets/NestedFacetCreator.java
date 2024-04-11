package de.cxp.ocs.elasticsearch.facets;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder.BucketCardinality;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.util.DefaultLinkBuilder;
import lombok.AccessLevel;
import lombok.Getter;
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

	@Getter(value = AccessLevel.PACKAGE)
	private final Map<String, FacetConfig> facetConfigs;
	private final Function<String, FacetConfig>	defaultFacetConfigProvider;

	@Getter(value = AccessLevel.PACKAGE)
	@Setter
	@NonNull
	private Set<String> generalExcludedFields = Collections.emptySet();

	public NestedFacetCreator(Map<String, FacetConfig> facetConfigs, Function<String, FacetConfig> defaultFacetConfigProvider) {
		this.facetConfigs = facetConfigs;
		if (defaultFacetConfigProvider == null) {
			this.defaultFacetConfigProvider = name -> new FacetConfig(name, name);
		}
		else {
			this.defaultFacetConfigProvider = defaultFacetConfigProvider;
		}
	}

	protected abstract String getNestedPath();

	protected abstract AggregationBuilder getNestedValueAggregation(String nestedPathPrefix);

	protected abstract boolean onlyFetchAggregationsForConfiguredFacets();

	protected abstract boolean correctedNestedDocumentCount();

	protected abstract boolean isMatchingFilterType(InternalResultFilter internalResultFilter);

	protected abstract Optional<Facet> createFacet(Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter facetFilter, DefaultLinkBuilder linkBuilder);

	@Override
	public AggregationBuilder buildAggregation(FilterContext filterContext) {
		return buildFilteredAggregation(filterContext, Collections.emptySet(), Collections.emptySet());
	}

	@Override
	public AggregationBuilder buildIncludeFilteredAggregation(FilterContext filterContext, Set<String> includeNames) {
		return buildFilteredAggregation(filterContext, includeNames, Collections.emptySet());
	}

	@Override
	public AggregationBuilder buildExcludeFilteredAggregation(FilterContext filterContext, Set<String> excludeNames) {
		return buildFilteredAggregation(filterContext, Collections.emptySet(), excludeNames);
	}

	private AggregationBuilder buildFilteredAggregation(FilterContext filterContext, Set<String> includedNames, Set<String> excludedNames) {
		String nestedPathPrefix = "";
		if (nestedFacetCorrector != null) nestedPathPrefix = nestedFacetCorrector.getNestedPathPrefix();
		nestedPathPrefix += getNestedPath();

		Set<String> actualIncludes = getActualIncludedNames(includedNames, excludedNames);
		Map<String, InternalResultFilter> facetFilters = getFilteredFacetNamesAndIds(filterContext, actualIncludes);

		AggregationBuilder nestedAggregation = AggregationBuilders.nested(uniqueAggregationName, nestedPathPrefix);

		String nestedFilterNamePath = nestedPathPrefix + ".name";
		if (!facetFilters.isEmpty()) {
			String nestedFilterIdPath = nestedPathPrefix + ".id";
			String nestedFilterValuePath = nestedPathPrefix + ".value";
			// ensure mutable set
			excludedNames = new HashSet<>(excludedNames);

			for (Entry<String, InternalResultFilter> facetFilterEntry : facetFilters.entrySet()) {
				String filteredName = facetFilterEntry.getKey();
				InternalResultFilter facetFilter = facetFilterEntry.getValue();
				BoolQueryBuilder facetFilterQuery = QueryBuilders.boolQuery()
						.must(QueryBuilders.termsQuery(nestedFilterNamePath, filteredName))
						.must(QueryBuilders.termsQuery(facetFilter.isFilterOnId() ? nestedFilterIdPath : nestedFilterValuePath, facetFilter.getValues()));
				nestedAggregation.subAggregation(buildSubAggregation(nestedPathPrefix, filteredName, facetFilterQuery));

				excludedNames.add(filteredName);
				actualIncludes.remove(filteredName);
			}
		}

		if (!this.onlyFetchAggregationsForConfiguredFacets() || !actualIncludes.isEmpty() || facetFilters.isEmpty()) {
			QueryBuilder nameFilter = getNameFilter(nestedFilterNamePath, actualIncludes, excludedNames);
			nestedAggregation.subAggregation(buildSubAggregation(nestedPathPrefix, null, nameFilter));
		}

		return nestedAggregation;
	}

	/**
	 * Check if there are facets sensitive to filters, meaning that those facets has to be filtered by the provided
	 * IDs as well.
	 * 
	 * @param filterContext
	 * @param actualIncludes
	 * @return the names and the according IDs to be filtered
	 */
	private Map<String, InternalResultFilter> getFilteredFacetNamesAndIds(FilterContext filterContext, Set<String> actualIncludes) {
		if (filterContext == null || filterContext.getInternalFilters().isEmpty() || actualIncludes.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, InternalResultFilter> filteredFacetIds = new HashMap<>();
		facetConfigs.values().stream()
				.filter(facetConfig -> facetConfig.isFilterSensitive())
				.filter(facetConfig -> actualIncludes.contains(facetConfig.getSourceField()))
				.map(facetConfig -> filterContext.getInternalFilters().get(facetConfig.getSourceField()))
				.filter(Objects::nonNull)
				.forEach(filter -> filteredFacetIds.put(filter.getField().getName(), filter));
		return filteredFacetIds;
	}

	private FilterAggregationBuilder buildSubAggregation(String nestedPathPrefix, String aggregationNameSuffix, QueryBuilder facetFilterQuery) {
		String filteredAggName = FILTERED_AGG;
		if (aggregationNameSuffix != null) {
			filteredAggName += "::" + aggregationNameSuffix;
		}
		String nestedFilterNamePath = nestedPathPrefix + ".name";
		return AggregationBuilders.filter(filteredAggName, facetFilterQuery)
				.subAggregation(
						AggregationBuilders.terms(FACET_NAMES_AGG)
								.field(nestedFilterNamePath)
								.size(maxFacets)
								.subAggregation(createValueAggregation(nestedPathPrefix)));
	}

	private AggregationBuilder createValueAggregation(String nestedPathPrefix) {
		AggregationBuilder valueAggBuilder = getNestedValueAggregation(nestedPathPrefix);
		if (nestedFacetCorrector != null && valueAggBuilder.bucketCardinality() != BucketCardinality.NONE && correctedNestedDocumentCount()) nestedFacetCorrector.correctValueAggBuilder(valueAggBuilder);
		return valueAggBuilder;
	}

	private QueryBuilder getNameFilter(String nestedFilterNamePath, Set<String> finalIncludes, Set<String> excludes) {
		final QueryBuilder nameFilter;

		if (finalIncludes.isEmpty()) {
			if (onlyFetchAggregationsForConfiguredFacets()) {
				// special case: this facet creator was configured to deliver
				// facets only for the configured fields, however these fields
				// were all excluded. So make sure the aggregation returns empty
				// result
				nameFilter = QueryBuilders.wrapperQuery("{\"match_none\": {}}");
			}
			else {
				Set<String> finalExcludes = new HashSet<>(excludes);
				finalExcludes.addAll(generalExcludedFields);
				if (finalExcludes.isEmpty()) {
					// no includes and no excludes defined
					nameFilter = QueryBuilders.matchAllQuery();
				}
				else {
					nameFilter = QueryBuilders.boolQuery().mustNot(QueryBuilders.termsQuery(nestedFilterNamePath, finalExcludes));
				}
			}
		}
		else {
			nameFilter = QueryBuilders.boolQuery().must(QueryBuilders.termsQuery(nestedFilterNamePath, finalIncludes));
		}
		return nameFilter;
	}

	private Set<String> getActualIncludedNames(Set<String> includedNames, Set<String> excludes) {
		Set<String> finalIncludes = new HashSet<>();
		if (onlyFetchAggregationsForConfiguredFacets()) {
			finalIncludes.addAll(facetConfigs.keySet());

			// if we have an explicit include list, use the intersection of
			// those both lists
			if (!includedNames.isEmpty()) {
				finalIncludes.retainAll(includedNames);
			}
		}
		else {
			finalIncludes.addAll(includedNames);
		}

		finalIncludes.removeAll(generalExcludedFields);
		finalIncludes.removeAll(excludes);
		return finalIncludes;
	}

	@Override
	public Collection<Facet> createFacets(Aggregations aggResult, FilterContext filterContext, DefaultLinkBuilder linkBuilder) {
		Nested nestedAggResult = ((Nested) aggResult.get(uniqueAggregationName));
		
		List<Facet> extractedFacets = new ArrayList<>();
		for (Aggregation filtersAgg : nestedAggResult.getAggregations()) {
			Terms facetNamesAggregation = ((ParsedFilter) filtersAgg).getAggregations().get(FACET_NAMES_AGG);
			extractedFacets.addAll(extractFacets(facetNamesAggregation, filterContext, linkBuilder));
		}
		
		// ParsedFilter filtersAgg = nestedAggResult.getAggregations().get(FILTERED_AGG);
		// if (filtersAgg == null) return Collections.emptyList();

		

		return extractedFacets;
	}

	protected List<Facet> extractFacets(Terms facetNames, FilterContext filterContext, DefaultLinkBuilder linkBuilder) {
		List<Facet> facets = new ArrayList<>();
		for (Terms.Bucket facetNameBucket : facetNames.getBuckets()) {
			String facetName = facetNameBucket.getKeyAsString();

			FacetConfig facetConfig = facetConfigs.get(facetName);
			if (facetConfig == null) facetConfig = defaultFacetConfigProvider.apply(facetName);

			InternalResultFilter facetFilter = filterContext.getInternalFilters().get(facetName);

			createFacet(facetNameBucket, facetConfig, facetFilter, linkBuilder).ifPresent(facets::add);
		}
		return facets;
	}


}
