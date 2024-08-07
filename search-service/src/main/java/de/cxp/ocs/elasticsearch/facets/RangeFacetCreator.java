package de.cxp.ocs.elasticsearch.facets;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.aggregations.metrics.ParsedStats;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FacetType;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.IntervalFacetEntry;
import de.cxp.ocs.model.result.RangeFacetEntry;
import de.cxp.ocs.util.DefaultLinkBuilder;
import lombok.Setter;
import lombok.experimental.Accessors;

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
@Accessors(chain = true)
public class RangeFacetCreator extends NestedFacetCreator {

	public final static String AGGREGATION_NAME = "_stats";

	/**
	 * Set to true, if this facet creator should only be used to create the configured facets. This should remain false
	 * for a default facet creator.
	 */
	@Setter
	private boolean isExplicitFacetCreator = false;

	public RangeFacetCreator(Map<String, FacetConfig> facetConfigs, Function<String, FacetConfig> defaultFacetConfigProvider) {
		super(facetConfigs, defaultFacetConfigProvider);
		// ensure the according filters are applied as post filters
		facetConfigs.values().forEach(c -> c.setShowUnselectedOptions(true));
	}

	@Override
	protected String getNestedPath() {
		return FieldConstants.NUMBER_FACET_DATA;
	}

	@Override
	protected boolean onlyFetchAggregationsForConfiguredFacets() {
		return isExplicitFacetCreator;
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
	protected Optional<Facet> createFacet(Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter facetFilter, DefaultLinkBuilder linkBuilder) {
		ParsedStats stats = facetNameBucket.getAggregations().get(AGGREGATION_NAME);
		if (stats.getMin() == stats.getMax() || stats.getCount() == 1) {
			return Optional.empty();
		}
		else {
			// work around floating-point imprecision that might lead to ugly values
			BigDecimal lowerBound = new BigDecimal(stats.getMin()).setScale(2, RoundingMode.HALF_DOWN);
			BigDecimal upperBound = new BigDecimal(stats.getMax()).setScale(2, RoundingMode.HALF_UP);
			RangeFacetEntry rangeFacetEntry = new RangeFacetEntry(lowerBound.doubleValue(), upperBound.doubleValue(), stats.getCount(), linkBuilder.toString(), facetFilter != null);
			if (facetFilter != null && !facetFilter.isNegated() && facetFilter instanceof NumberResultFilter) {
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
