package de.cxp.ocs.elasticsearch.facets;

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

public class RatingFacetCreator implements CustomFacetCreator {

	public static final String	RANGED_INTERVAL_FACET_TYPE	= "fixed_ranges";
	private static final String	RANGED_INTERVAL_AGG_NAME	= "FixedRangesAgg";

	@Override
	public String getFacetType() {
		return RANGED_INTERVAL_FACET_TYPE;
	}

	@Override
	public FieldType getAcceptibleFieldType() {
		return FieldType.NUMBER;
	}

	@Override
	public AggregationBuilder buildAggregation(String fullFieldName) {
        // create value aggregation
		return new RangeAggregationBuilder(RANGED_INTERVAL_AGG_NAME)
				.field(fullFieldName)
                .addRange(1,5).addRange(2,5).addRange(3,5).addRange(4,5);
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
		ParsedRange facetValues = (facetNameBucket.getAggregations().get(RANGED_INTERVAL_AGG_NAME));
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
            absDocCount += docCount;

            facet.addEntry(createIntervalFacetEntry(currentEntryBuilder, selectedFilter, facetConfig, linkBuilder));
            currentEntryBuilder = new NumericFacetEntryBuilder();
        }

        facet.setAbsoluteFacetCoverage(absDocCount);
    }

	private long getDocCount(Bucket facetNameBucket, Function<MultiBucketsAggregation.Bucket, Long> nestedValueBucketDocCountCorrector) {
        long absFacetCoverage = 0;
		if (nestedValueBucketDocCountCorrector != null) {
			ParsedRange facetValues = facetNameBucket.getAggregations().get(RANGED_INTERVAL_AGG_NAME);
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
		return new IntervalFacetEntry(currentValueInterval.lowerBound, currentValueInterval.upperBound, currentValueInterval.currentDocumentCount,
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
