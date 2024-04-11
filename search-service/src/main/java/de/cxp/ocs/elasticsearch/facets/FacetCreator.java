package de.cxp.ocs.elasticsearch.facets;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.util.DefaultLinkBuilder;

public interface FacetCreator {

	/**
	 * Build aggregation that is necessary to create the according facets.
	 * 
	 * @return configured aggregation-builder
	 */
	AggregationBuilder buildAggregation(FilterContext filterContext);

	/**
	 * Build aggregation that is necessary to create the facets specified by the
	 * includes list.
	 * 
	 * @param includeNames
	 *        names of data fields for which the aggregations should be built
	 * @return configured aggregation-builder
	 */
	AggregationBuilder buildIncludeFilteredAggregation(FilterContext filterContext, Set<String> includeNames);

	/**
	 * Build aggregation that is necessary to create the facets, but not the
	 * ones in the exlude list.
	 * 
	 * @param excludeNames
	 *        names of data fields that MUST NOT be part of this aggregation
	 * @return configured aggregation-builder
	 */
	AggregationBuilder buildExcludeFilteredAggregation(FilterContext filterContext, Set<String> excludeNames);

	/**
	 * create facets from aggregation result.
	 * 
	 * @param aggResult
	 *        ES aggregagtion result
	 * @param filterContext
	 *        the filter context
	 * @param linkBuilder
	 *        a link builder to create facet-entry-links
	 * @return a list of facets that can be derived from the aggregation result
	 */
	Collection<Facet> createFacets(Aggregations aggResult, FilterContext filterContext, DefaultLinkBuilder linkBuilder);

	/**
	 * Try to merge facets with same label. If not possible, return
	 * Optional.empty.
	 * 
	 * @param first
	 *        Facet A
	 * @param second
	 *        Facet B
	 * @return optionally a merged Facet, otherwise Optional::empty
	 */
	Optional<Facet> mergeFacets(Facet first, Facet second);
}
