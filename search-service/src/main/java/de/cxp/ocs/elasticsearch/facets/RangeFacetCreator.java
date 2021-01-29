package de.cxp.ocs.elasticsearch.facets;

import java.util.Map;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.aggregations.metrics.ParsedStats;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FacetType;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.IntervalFacetEntry;
import de.cxp.ocs.util.SearchQueryBuilder;

/**
 * <p>
 * Creates a facet with a single {@link IntervalFacetEntry} that contains the
 * global min and max value.
 * (Therefore it uses the "stats" aggregation - not the range aggregationas the
 * name might imply.)
 * </p>
 * <p>
 * At the moment, a selected filter range is represented at the returned link of
 * that {@link IntervalFacetEntry}.
 * </p>
 * TODO: add RangeFacetEntry with absolute min/max and selected min/max
 */
public class RangeFacetCreator extends NestedFacetCreator {

	public final static String AGGREGATION_NAME = "_stats";
	
	public RangeFacetCreator(Map<String, FacetConfig> facetConfigs) {
		super(facetConfigs);
		// ensure the according filters are applied as post filters
		facetConfigs.values().forEach(c -> c.setShowUnselectedOptions(true));
	}

	@Override
	protected String getNestedPath() {
		return FieldConstants.NUMBER_FACET_DATA;
	}

	@Override
	protected boolean onlyFetchAggregationsForConfiguredFacets() {
		return true;
	}

	@Override
	protected boolean correctedNestedDocumentCount() {
		return false;
	}

	@Override
	protected AggregationBuilder getNestedValueAggregation(String nestedPathPrefix) {
		return AggregationBuilders.stats(AGGREGATION_NAME)
				.field(nestedPathPrefix + ".value");
	}

	@Override
	protected Facet createFacet(Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter facetFilter, SearchQueryBuilder linkBuilder) {
		ParsedStats stats = facetNameBucket.getAggregations().get(AGGREGATION_NAME);
		Facet facet = FacetFactory.create(facetConfig, FacetType.range.name());
		boolean isSelected = facetFilter != null;
		facet.addEntry(new IntervalFacetEntry(stats.getMin(), stats.getMax(), -1, linkBuilder.toString(), isSelected));
		return facet;
	}

}
