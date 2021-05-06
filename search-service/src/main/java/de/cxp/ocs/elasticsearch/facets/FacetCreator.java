package de.cxp.ocs.elasticsearch.facets;

import java.util.Collection;
import java.util.Set;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.util.SearchQueryBuilder;

public interface FacetCreator {

	Collection<Facet> createFacets(Aggregations aggResult, FilterContext filterContext, SearchQueryBuilder linkBuilder);

	AggregationBuilder buildAggregation();

	AggregationBuilder buildIncludeFilteredAggregation(Set<String> includeNames);

	AggregationBuilder buildExcludeFilteredAggregation(Set<String> excludeNames);
}
