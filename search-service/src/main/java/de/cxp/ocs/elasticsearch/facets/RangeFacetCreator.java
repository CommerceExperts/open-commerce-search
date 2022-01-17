package de.cxp.ocs.elasticsearch.facets;

import java.util.Map;
import java.util.Optional;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.aggregations.metrics.ParsedStats;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FacetType;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.IntervalFacetEntry;
import de.cxp.ocs.model.result.RangeFacetEntry;
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
	protected boolean isMatchingFilterType(InternalResultFilter internalResultFilter) {
		return internalResultFilter instanceof NumberResultFilter;
	}

	@Override
	protected AggregationBuilder getNestedValueAggregation(String nestedPathPrefix) {
		return AggregationBuilders.stats(AGGREGATION_NAME)
				.field(nestedPathPrefix + ".value");
	}

	@Override
	protected Optional<Facet> createFacet(Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter facetFilter, SearchQueryBuilder linkBuilder) {
		ParsedStats stats = facetNameBucket.getAggregations().get(AGGREGATION_NAME);
		if (stats.getMin() == stats.getMax() || stats.getCount() == 1) {
			return Optional.empty();
		}
		else {
			RangeFacetEntry rangeFacetEntry = new RangeFacetEntry(stats.getMin(), stats.getMax(), stats.getCount(), linkBuilder.toString(), facetFilter != null);
			if (facetFilter != null && facetFilter instanceof NumberResultFilter) {
				rangeFacetEntry.setSelectedMin(((NumberResultFilter) facetFilter).getLowerBound());
				rangeFacetEntry.setSelectedMax(((NumberResultFilter) facetFilter).getUpperBound());
			}
			return Optional.of(
					FacetFactory.create(facetConfig, FacetType.RANGE)
							.setAbsoluteFacetCoverage(stats.getCount())
							.addEntry(rangeFacetEntry));
		}
	}

	@Override
	public Optional<Facet> mergeFacets(Facet first, Facet second) {
		if (!first.getFieldName().equals(second.getFieldName())
				|| !FacetType.RANGE.name().toLowerCase().equals(first.getType())
				|| !first.getType().equals(second.getType())) {
			return Optional.empty();
		}

		RangeFacetEntry firstEntry = (RangeFacetEntry) first.getEntries().get(0);
		RangeFacetEntry secondEntry = (RangeFacetEntry) second.getEntries().get(0);

		if (secondEntry.getLowerBound().doubleValue() < firstEntry.getLowerBound().doubleValue()) {
			firstEntry.setLowerBound(secondEntry.getLowerBound());
		}
		if (secondEntry.getUpperBound().doubleValue() > firstEntry.getUpperBound().doubleValue()) {
			firstEntry.setUpperBound(secondEntry.getUpperBound());
		}

		return Optional.of(first);
	}

}
