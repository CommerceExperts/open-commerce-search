package de.cxp.ocs.elasticsearch.facets;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.util.SearchQueryBuilder;

public interface FacetCreator {

	/**
	 * Build aggregation that is necessary to create the according facets.
	 * 
	 * @return
	 */
	AggregationBuilder buildAggregation();

	/**
	 * 
	 * Build aggregation that is necessary to create the facets specified by the
	 * includes list.
	 * 
	 * @param includeNames
	 * @return
	 */
	AggregationBuilder buildIncludeFilteredAggregation(Set<String> includeNames);

	/**
	 * Build aggregation that is necessary to create the facets, but not the
	 * ones in the exlude list.
	 * 
	 * @param excludeNames
	 * @return
	 */
	AggregationBuilder buildExcludeFilteredAggregation(Set<String> excludeNames);

	/**
	 * create facets from aggregation result.
	 * 
	 * @param aggResult
	 * @param filterContext
	 * @param linkBuilder
	 * @return
	 */
	Collection<Facet> createFacets(Aggregations aggResult, FilterContext filterContext, SearchQueryBuilder linkBuilder);

	/**
	 * Try to merge facets with same label. If not possible, return
	 * Optional.empty.
	 * 
	 * @param first
	 * @param second
	 * @return
	 */
	Optional<Facet> mergeFacets(Facet first, Facet second);
}
