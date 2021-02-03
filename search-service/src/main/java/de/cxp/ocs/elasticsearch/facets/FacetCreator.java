package de.cxp.ocs.elasticsearch.facets;

import java.util.Collection;
import java.util.Set;

import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.util.SearchQueryBuilder;

public interface FacetCreator {

	AbstractAggregationBuilder<?> buildAggregation(FilterContext filters);

	Collection<Facet> createFacets(Aggregations aggResult, FilterContext filterContext, SearchQueryBuilder linkBuilder);

	AggregationBuilder buildAggregationWithNamesExcluded(FilterContext filterContext, Set<String> excludedNames);

}
