package de.cxp.ocs.elasticsearch.facets;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.spi.search.CustomFacetCreator;
import de.cxp.ocs.util.DefaultLinkBuilder;
import de.cxp.ocs.util.LinkBuilder;

class ConfiguredIntervalFacetCreator implements CustomFacetCreator {

	private final IntervalFacetCreator delegateFC;

	public ConfiguredIntervalFacetCreator(int interval) {
		delegateFC = new IntervalFacetCreator(Collections.emptyMap(), null);
		delegateFC.setInterval(interval);
	}

	@Override
	public String getFacetType() {
		return "interval_" + delegateFC.getInterval();
	}

	@Override
	public FieldType getAcceptibleFieldType() {
		return FieldType.NUMBER;
	}

	@Override
	public AggregationBuilder buildAggregation(String fullFieldName) {
		return AggregationBuilders.histogram(IntervalFacetCreator.FACET_VALUES_AGG)
				.field(fullFieldName)
				.interval(delegateFC.getInterval())
				.minDocCount(1);
	}

	@Override
	public Optional<Facet> createFacet(Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter facetFilter, LinkBuilder linkBuilder, Function<org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation.Bucket, Long> nestedValueBucketDocCountCorrector) {
		return delegateFC.createFacet(facetNameBucket, facetConfig, facetFilter, (DefaultLinkBuilder) linkBuilder);
	}

	@Override
	public Optional<Facet> mergeFacets(Facet first, Facet second) {
		return delegateFC.mergeFacets(first, second);
	}


}
