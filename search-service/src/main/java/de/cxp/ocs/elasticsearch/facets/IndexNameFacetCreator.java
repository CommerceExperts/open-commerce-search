package de.cxp.ocs.elasticsearch.facets;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FacetType;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.util.DefaultLinkBuilder;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class IndexNameFacetCreator implements FacetCreator {

	private final FacetConfig indexFacetConfig;

	@Override
	public AggregationBuilder buildAggregation(FilterContext filterContext) {
		return AggregationBuilders.terms("_indexes").field("_index");
	}

	@Override
	public AggregationBuilder buildIncludeFilteredAggregation(FilterContext filterContext, Set<String> includeNames) {
		// no filtering possible
		return buildAggregation(filterContext);
	}

	@Override
	public AggregationBuilder buildExcludeFilteredAggregation(FilterContext filterContext, Set<String> excludeNames) {
		// no filtering possible
		return buildAggregation(filterContext);
	}

	@Override
	public Collection<Facet> createFacets(Aggregations aggResult, FilterContext filterContext, DefaultLinkBuilder linkBuilder) {
		Terms indexTermAggResult = aggResult.get("_indexes");
		if (indexTermAggResult == null) return Collections.emptyList();
		
		Facet facet = FacetFactory.create(indexFacetConfig, FacetType.TERM);
		long[] sum = { 0L };
		indexTermAggResult.getBuckets().forEach(bucket -> {
			facet.addEntry(new FacetEntry(bucket.getKeyAsString(), null, bucket.getDocCount(), null, false));
			sum[0] += bucket.getDocCount();
		});
		facet.absoluteFacetCoverage = sum[0];
		return Collections.singletonList(facet);
	}

	@Override
	public Optional<Facet> mergeFacets(Facet first, Facet second) {
		// not necessary, because this should never happen
		return Optional.of(first);
	}

}
