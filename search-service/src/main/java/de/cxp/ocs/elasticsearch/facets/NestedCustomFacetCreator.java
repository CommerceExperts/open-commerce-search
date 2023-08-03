package de.cxp.ocs.elasticsearch.facets;

import java.util.Map;
import java.util.Optional;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.PathResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.spi.search.CustomFacetCreator;
import de.cxp.ocs.util.DefaultLinkBuilder;
import de.cxp.ocs.util.LinkBuilder;
import lombok.NonNull;

public class NestedCustomFacetCreator extends NestedFacetCreator {

	private final FieldType fieldType;
	private final CustomFacetCreator customFacetCreator;
	
	public NestedCustomFacetCreator(Map<String, FacetConfig> facetConfigs, @NonNull FieldType fieldType, @NonNull CustomFacetCreator customFacetCreator) {
		super(facetConfigs, null);
		this.fieldType = fieldType;
		this.customFacetCreator = customFacetCreator;
		this.setUniqueAggregationName("CustomFacetAgg_" + customFacetCreator.getFacetType());
	}

	@Override
	protected boolean onlyFetchAggregationsForConfiguredFacets() {
		// this facet creator is only for the few custom facets and should restrict the aggregation to those fields
		return true;
	}

	@Override
	protected String getNestedPath() {
		switch (fieldType) {
			case CATEGORY:
				return FieldConstants.PATH_FACET_DATA;
			case NUMBER:
				return FieldConstants.NUMBER_FACET_DATA;
			default:
				return FieldConstants.TERM_FACET_DATA;
		}
	}

	@Override
	protected AggregationBuilder getNestedValueAggregation(String nestedPathPrefix) {
		return customFacetCreator.buildAggregation(nestedPathPrefix + ".value");
	}

	@Override
	protected Optional<Facet> createFacet(Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter facetFilter, DefaultLinkBuilder linkBuilder) {
		return customFacetCreator.createFacet(facetNameBucket, facetConfig, facetFilter, (LinkBuilder) linkBuilder, nestedFacetCorrector::getCorrectedDocumentCount);
	}

	@Override
	protected boolean correctedNestedDocumentCount() {
		return true;
	}

	@Override
	public Optional<Facet> mergeFacets(Facet first, Facet second) {
		return customFacetCreator.mergeFacets(first, second);
	}

	@Override
	protected boolean isMatchingFilterType(InternalResultFilter internalResultFilter) {
		switch (fieldType) {
			case CATEGORY:
				return internalResultFilter instanceof PathResultFilter;
			case NUMBER:
				return internalResultFilter instanceof NumberResultFilter;
			default:
				return internalResultFilter instanceof TermResultFilter;
		}
	}

}
