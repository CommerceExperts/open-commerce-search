package de.cxp.ocs.elasticsearch.facets;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.range.ParsedRange;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;

import de.cxp.ocs.config.FacetConfiguration;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FacetType;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.elasticsearch.facets.IntervalFacetCreator.NumericFacetEntryBuilder;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.IntervalFacetEntry;
import de.cxp.ocs.spi.search.CustomFacetCreator;
import de.cxp.ocs.util.LinkBuilder;

/**
 * The basis for a interval facet creator that should extract facets with fixed intervals.
 * 
 * <p>
 * Why is it not possible to have a configurable FixedIntervalFacetCreator?
 * Because a single facet creator will be used for all facets it is configured for. So it is not possible to configure
 * one FixedIntervalFacetCreator for different facets with different interval ranges.
 * </p>
 * <p>
 * That's why one would need to create for example a "RatingFacetCreator" and a "FixedPriceIntervalFacetCreator" with
 * different ranges and different "custom facet types" to meet its requirements.
 * </p>
 * <p>
 * Unless "configurable single-use facet creators" are not supported, this is the (only?) way to support that kind of
 * requirement with minimal effort.
 * </p>
 * 
 * @author rb@commerce-experts.com
 */
public abstract class FixedIntervalFacetCreator implements CustomFacetCreator {

	private static final String RANGED_INTERVAL_AGG_NAME_PREFIX = "FixedRangesAgg_";

	/**
	 * <p>
	 * A restricted range to numeric values only.
	 * </p>
	 * <p>
	 * Note that the upper bound is exclusive, so a range from 1-5 won't contain the records with value 5 and a range
	 * from 1-1 will yield no results at all. So to have an "inclusive range" for such values use for example 1.0 - 5.01
	 * and to hide the fact of that range, use an according range label.
	 * </p>
	 * <p>
	 * Although open ranges are possible by setting one of from and to to null, the created filters are invalid since
	 * they will contain the text "Infinite" which can't be parsed by OCS. To achieve open ranges, consider using your
	 * own high values and a custom range label.
	 * </p>
	 */
	public static class NumericRange extends org.elasticsearch.search.aggregations.bucket.range.RangeAggregator.Range {

		public NumericRange(String rangeLabel, Double from, Double to) {
			super(rangeLabel, from, to);
		}

		public NumericRange(Double from, Double to) {
			super(null, from, to);
		}

		public NumericRange(Integer from, Integer to) {
			super(null, from != null ? from.doubleValue() : null, to != null ? to.doubleValue() : null);
		}

		public NumericRange(String rangeLabel, Integer from, Integer to) {
			super(rangeLabel, from != null ? from.doubleValue() : null, to != null ? to.doubleValue() : null);
		}

	}

	private final List<NumericRange> fixedRanges;

	public FixedIntervalFacetCreator(List<NumericRange> ranges) {
		assert ranges.size() > 0;
		fixedRanges = new ArrayList<>(ranges);
	}

	@Override
	public FieldType getAcceptibleFieldType() {
		return FieldType.NUMBER;
	}

	protected String getUniqAggregationName() {
		return RANGED_INTERVAL_AGG_NAME_PREFIX + this.getClass().getSimpleName();
	}

	@Override
	public AggregationBuilder buildAggregation(String fullFieldName) {
		// create value aggregation
		RangeAggregationBuilder rangeAggregationBuilder = new RangeAggregationBuilder(getUniqAggregationName())
				.field(fullFieldName);
		fixedRanges.forEach(rangeAggregationBuilder::addRange);
		return rangeAggregationBuilder;
	}

	@Override
	public Optional<Facet> createFacet(Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter facetFilter, LinkBuilder linkBuilder, Function<MultiBucketsAggregation.Bucket, Long> nestedValueBucketDocCountCorrector) {
		Facet facet = FacetFactory.create(facetConfig, FacetType.INTERVAL);
		if (facetFilter instanceof NumberResultFilter) {
			if (!facetConfig.isMultiSelect() && !facetConfig.isShowUnselectedOptions()) {
				// filtered single select facet
				long docCount = getDocCount(facetNameBucket, nestedValueBucketDocCountCorrector);
				NumericFacetEntryBuilder facetEntry = new NumericFacetEntryBuilder(((NumberResultFilter) facetFilter));
				facetEntry.currentDocumentCount = docCount;
				facetEntry.currentVariantCount = (int) docCount;
				facet.addEntry(createIntervalFacetEntry(facetEntry, (NumberResultFilter) facetFilter, facetConfig, linkBuilder));
				facet.setAbsoluteFacetCoverage(docCount);
			}
			else {
				// multiselect facet
				fillFacet(facetNameBucket, facet, facetConfig, linkBuilder, (NumberResultFilter) facetFilter, nestedValueBucketDocCountCorrector);
			}
		}
		else {
			// unfiltered facet
			fillFacet(facetNameBucket, facet, facetConfig, linkBuilder, null, nestedValueBucketDocCountCorrector);
		}
		return facet.entries.isEmpty() ? Optional.empty() : Optional.of(facet);
	}

	protected <T extends Number> void fillFacet(Bucket facetNameBucket, Facet facet, FacetConfiguration.FacetConfig facetConfig, LinkBuilder linkBuilder, NumberResultFilter selectedFilter, Function<MultiBucketsAggregation.Bucket, Long> nestedValueBucketDocCountCorrector) {
		ParsedRange facetValues = (facetNameBucket.getAggregations().get(getUniqAggregationName()));
		List<? extends Range.Bucket> valueBuckets = facetValues.getBuckets();

		NumericFacetEntryBuilder currentEntryBuilder = new NumericFacetEntryBuilder();
		long absDocCount = 0;
		for (Range.Bucket valueBucket : valueBuckets) {

			currentEntryBuilder.lowerBound = (Double) valueBucket.getFrom();
			long docCount = nestedValueBucketDocCountCorrector != null
					? nestedValueBucketDocCountCorrector.apply(valueBucket)
					: valueBucket.getDocCount();
			currentEntryBuilder.currentVariantCount += valueBucket.getDocCount();
			currentEntryBuilder.currentDocumentCount += docCount;
			currentEntryBuilder.upperBound = (Double) valueBucket.getTo();
			currentEntryBuilder.key = valueBucket.getKeyAsString();

			absDocCount += docCount;

			facet.addEntry(createIntervalFacetEntry(currentEntryBuilder, selectedFilter, facetConfig, linkBuilder));
			currentEntryBuilder = new NumericFacetEntryBuilder();
		}

		facet.setAbsoluteFacetCoverage(absDocCount);
	}

	private long getDocCount(Bucket facetNameBucket, Function<MultiBucketsAggregation.Bucket, Long> nestedValueBucketDocCountCorrector) {
		long absFacetCoverage = 0;
		if (nestedValueBucketDocCountCorrector != null) {
			ParsedRange facetValues = facetNameBucket.getAggregations().get(getUniqAggregationName());
			for (Range.Bucket valueBucket : facetValues.getBuckets()) {
				absFacetCoverage += nestedValueBucketDocCountCorrector.apply(valueBucket);
			}
		}
		else {
			absFacetCoverage = facetNameBucket.getDocCount();
		}
		return absFacetCoverage;
	}

	protected FacetEntry createIntervalFacetEntry(NumericFacetEntryBuilder currentValueInterval, NumberResultFilter selectedFilter, FacetConfig facetConfig, LinkBuilder linkBuilder) {
		boolean isSelected = selectedFilter != null
				&& selectedFilter.getLowerBound().floatValue() == currentValueInterval.lowerBound.floatValue()
				&& selectedFilter.getUpperBound().floatValue() == currentValueInterval.upperBound.floatValue();
		return new IntervalFacetEntry(currentValueInterval.key, currentValueInterval.lowerBound, currentValueInterval.upperBound, currentValueInterval.currentDocumentCount,
				isSelected ? linkBuilder.withoutFilterAsLink(facetConfig.getSourceField()) : linkBuilder.withFilterAsLink(facetConfig.getSourceField(), false, currentValueInterval.getFilterValues()),
				isSelected);
	}

	@Override
	public Optional<Facet> mergeFacets(Facet first, Facet second) {
		if (first.getEntries().isEmpty() || first.absoluteFacetCoverage < second.absoluteFacetCoverage) return Optional.of(second);
		if (second.getEntries().isEmpty() || first.absoluteFacetCoverage > second.absoluteFacetCoverage) return Optional.of(first);
		// TODO: if there are range facets with different intervals and different counts from master & variant level,
		// consider a proper merge logic.
		return Optional.of(first);
	}
}
