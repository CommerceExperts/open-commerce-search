package de.cxp.ocs.elasticsearch.facets;

import de.cxp.ocs.config.FacetConfiguration;
import de.cxp.ocs.config.FacetType;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.util.SearchQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.ParsedRange;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

import java.util.*;
import java.util.function.Function;

public class RatingFacetCreator extends IntervalFacetCreator implements CustomFacetCreator {

    public RatingFacetCreator(Map<String, FacetConfiguration.FacetConfig> facetConfigs, Function<String, FacetConfiguration.FacetConfig> defaultFacetConfigProvider) {
        super(facetConfigs, defaultFacetConfigProvider);
    }

    @Override
    protected AggregationBuilder getNestedValueAggregation(String nestedPathPrefix) {
        // create value aggregation
        return new RangeAggregationBuilder(FACET_VALUES_AGG)
                .field(nestedPathPrefix + ".value")
                .addRange(1,5).addRange(2,5).addRange(3,5).addRange(4,5);
    }

    @Override
    protected Optional<Facet> createFacet(Terms.Bucket facetNameBucket, FacetConfiguration.FacetConfig facetConfig, InternalResultFilter facetFilter,
                                          SearchQueryBuilder linkBuilder) {
        Facet facet = FacetFactory.create(facetConfig, FacetType.INTERVAL);
        if (facetFilter instanceof NumberResultFilter) {
            if (!facetConfig.isMultiSelect() && !facetConfig.isShowUnselectedOptions()) {
                // filtered single select facet
                long docCount = getDocCount(facetNameBucket);
                NumericFacetEntryBuilder facetEntry = new NumericFacetEntryBuilder(((NumberResultFilter) facetFilter));
                facetEntry.currentDocumentCount = docCount;
                facetEntry.currentVariantCount = (int) docCount;
                facet.addEntry(createIntervalFacetEntry(facetEntry, (NumberResultFilter) facetFilter, facetConfig, linkBuilder));
                facet.setAbsoluteFacetCoverage(docCount);
            }
            else {
                // multiselect facet
                fillFacet(facetNameBucket, facet, facetConfig, linkBuilder, (NumberResultFilter) facetFilter);
            }
        }
        else {
            // unfiltered facet
            fillFacet(facetNameBucket, facet, facetConfig, linkBuilder, null);
        }
        return facet.entries.isEmpty() ? Optional.empty() : Optional.of(facet);
    }

    protected <T extends Number> void fillFacet(Terms.Bucket facetNameBucket, Facet facet, FacetConfiguration.FacetConfig facetConfig, SearchQueryBuilder linkBuilder, NumberResultFilter selectedFilter) {
        ParsedRange facetValues = (facetNameBucket.getAggregations().get(FACET_VALUES_AGG));
        List<? extends Range.Bucket> valueBuckets = facetValues.getBuckets();

        NumericFacetEntryBuilder currentEntryBuilder = new NumericFacetEntryBuilder();
        long absDocCount = 0;
        for (Range.Bucket valueBucket : valueBuckets) {

            currentEntryBuilder.lowerBound = (Double) valueBucket.getFrom();
            long docCount = nestedFacetCorrector != null
                    ? nestedFacetCorrector.getCorrectedDocumentCount(valueBucket)
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

    private long getDocCount(Terms.Bucket facetNameBucket) {
        long absFacetCoverage = 0;
        if (nestedFacetCorrector != null) {
            ParsedRange facetValues = facetNameBucket.getAggregations().get(FACET_VALUES_AGG);
            for (Range.Bucket valueBucket : facetValues.getBuckets()) {
                absFacetCoverage += nestedFacetCorrector.getCorrectedDocumentCount(valueBucket);
            }
        }
        else {
            absFacetCoverage = facetNameBucket.getDocCount();
        }
        return absFacetCoverage;
    }
}
