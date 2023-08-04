package de.cxp.ocs.spi.search;

import java.util.Optional;
import java.util.function.Function;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.util.LinkBuilder;

public interface CustomFacetCreator {

	/**
	 * Return unique type name of facets that this creator can handle. That is the type that must be used in the facet
	 * configuration for a facet to have this creator building the according facet.
	 * It MUST NOT be one of the reserved facet types 'term', 'interval', 'range', 'hierarchical' or 'ignore'.
	 * 
	 * @return type name
	 */
	String getFacetType();

	/**
	 * Specify for which field type this creator works. Must be one of: STRING, NUMBER, CATEGORY.
	 * A facet creator can not work for different field types - in that case different implementation of the
	 * facet-creator with different
	 * 
	 * @return
	 */
	FieldType getAcceptibleFieldType();

	/**
	 * Build the (sub)-aggregation that should be applied on the values of each field with that facet-type. The same
	 * aggregation is used for all facets of that type.
	 * 
	 * @param fullFieldName
	 *        the 'full value field name' to be used as aggregation field-name.
	 * @return
	 */
	AggregationBuilder buildAggregation(String fullFieldName);

	/**
	 * Create a facet from the given aggregation result.
	 * 
	 * @param facetNameBucket
	 *        The terms bucket of that facet field. The key of that bucket is the field name. It contains the required
	 *        aggregation results as sub-aggregation.
	 * @param facetConfig
	 *        the config of the facet to be created
	 * @param facetFilter
	 *        a nullable filter if there is a filter applied for facet. Necessary to set the according values as
	 *        selected.
	 * @param linkBuilder
	 *        a helper to create links for facet values / filters.
	 * @return facet if it can be created otherwise an empty optional.
	 */
	Optional<Facet> createFacet(Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter facetFilter, LinkBuilder linkBuilder, Function<MultiBucketsAggregation.Bucket, Long> nestedValueBucketDocCountCorrector);

	/**
	 * In case such a custom facet should be created on a field that is indexed on variant and master level, two facets
	 * are created and should be merged.
	 * If that is not possible feel free to log an error and return Optional.empty or just one of those facets.
	 * 
	 * @param first
	 * @param second
	 * @return
	 */
	Optional<Facet> mergeFacets(Facet first, Facet second);

}
