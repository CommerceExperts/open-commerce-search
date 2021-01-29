package de.cxp.ocs.elasticsearch.facets;

import java.util.List;
import java.util.Map;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram.Bucket;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.IntervalFacetEntry;
import de.cxp.ocs.util.SearchQueryBuilder;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class IntervalFacetCreator extends NestedFacetCreator {

	private int wishedFacetSize = 5;

	// TODO: fetch statistics from the numeric ranges of each facet value to
	// use proper interval
	@Setter
	private int interval = 5;

	public IntervalFacetCreator(Map<String, FacetConfig> facetConfigs) {
		super(facetConfigs);
	}

	@Override
	protected String getNestedPath() {
		return FieldConstants.NUMBER_FACET_DATA;
	}

	@Override
	protected boolean onlyFetchAggregationsForConfiguredFacets() {
		return false;
	}

	@Override
	protected boolean correctedNestedDocumentCount() {
		return true;
	}

	@Override
	protected AggregationBuilder getNestedValueAggregation(String nestedPathPrefix) {
		// create value aggregation
		return AggregationBuilders.histogram(FACET_VALUES_AGG)
				.field(nestedPathPrefix + ".value")
				.interval(interval)
				.minDocCount(1);
	}

	@Override
	protected Facet createFacet(Terms.Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter facetFilter,
			SearchQueryBuilder linkBuilder) {
		Facet facet = FacetFactory.create(facetConfig, "interval");
		if (facetFilter != null && facetFilter instanceof NumberResultFilter) {
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
		return facet;
	}


	private long getDocCount(Terms.Bucket facetNameBucket) {
		long absFacetCoverage = 0;
		if (nestedFacetCorrector != null) {
			Histogram facetValues = ((Histogram) facetNameBucket.getAggregations().get(FACET_VALUES_AGG));
			for (Histogram.Bucket valueBucket : facetValues.getBuckets()) {
				absFacetCoverage += nestedFacetCorrector.getCorrectedDocumentCount(valueBucket);
			}
		}
		else {
			absFacetCoverage = facetNameBucket.getDocCount();
		}
		return absFacetCoverage;
	}

	private void fillFacet(Terms.Bucket facetNameBucket, Facet facet, FacetConfig facetConfig, SearchQueryBuilder linkBuilder, NumberResultFilter selectedFilter) {
		Histogram facetValues = ((Histogram) facetNameBucket.getAggregations().get(FACET_VALUES_AGG));
		List<? extends Bucket> valueBuckets = facetValues.getBuckets();

		long variantCount = facetNameBucket.getDocCount();
		long variantCountPerBucket = variantCount / (wishedFacetSize + 1);

		NumericFacetEntryBuilder currentEntryBuilder = new NumericFacetEntryBuilder();
		long absDocCount = 0;
		for (Histogram.Bucket valueBucket : valueBuckets) {
			if (currentEntryBuilder.currentDocumentCount == 0) {
				currentEntryBuilder.lowerBound = (Double) valueBucket.getKey();
			}
			Double value = (Double) valueBucket.getKey();

			long docCount = nestedFacetCorrector != null
					? nestedFacetCorrector.getCorrectedDocumentCount(valueBucket)
					: valueBucket.getDocCount();
			currentEntryBuilder.currentVariantCount += valueBucket.getDocCount();
			currentEntryBuilder.currentDocumentCount += docCount;
			currentEntryBuilder.upperBound = (Double) valueBucket.getKey() + interval - 0.01;
			absDocCount += docCount;

			if (currentEntryBuilder.currentVariantCount >= variantCountPerBucket) {
				facet.addEntry(createIntervalFacetEntry(currentEntryBuilder, selectedFilter, facetConfig, linkBuilder));
				currentEntryBuilder = new NumericFacetEntryBuilder();
			}
		}
		if (currentEntryBuilder.currentVariantCount > 0) {
			facet.addEntry(createIntervalFacetEntry(currentEntryBuilder, selectedFilter, facetConfig, linkBuilder));
		}

		facet.setAbsoluteFacetCoverage(absDocCount);
	}

	private FacetEntry createIntervalFacetEntry(NumericFacetEntryBuilder currentValueInterval, NumberResultFilter selectedFilter, FacetConfig facetConfig,
			SearchQueryBuilder linkBuilder) {
		boolean isSelected = selectedFilter != null
				&& selectedFilter.getLowerBound().floatValue() == currentValueInterval.lowerBound.floatValue()
				&& selectedFilter.getUpperBound().floatValue() == currentValueInterval.upperBound.floatValue();
		return new IntervalFacetEntry(currentValueInterval.lowerBound,
				currentValueInterval.upperBound,
				currentValueInterval.currentDocumentCount,
				isSelected ? linkBuilder.withoutFilterAsLink(facetConfig, currentValueInterval.getFilterValues())
						: linkBuilder.withFilterAsLink(facetConfig, currentValueInterval.getFilterValues()),
				isSelected);
	}

	@NoArgsConstructor
	private static class NumericFacetEntryBuilder {

		Double	lowerBound;
		Double	upperBound;
		long	currentDocumentCount	= 0;
		int		currentVariantCount		= 0;

		NumericFacetEntryBuilder(NumberResultFilter facetFilter) {
			Number lowerBoundValue = facetFilter.getLowerBound();
			Number upperBoundValue = facetFilter.getUpperBound();
			lowerBound = lowerBoundValue == null ? null : lowerBoundValue.doubleValue();
			upperBound = upperBoundValue == null ? null : upperBoundValue.doubleValue();
		}

		String[] getFilterValues() {
			if (lowerBound == null && upperBound == null) {
				return null;
			}
			if (lowerBound == null) {
				return new String[] { upperBound.toString() };
			}
			if (upperBound == null) {
				return new String[] { lowerBound.toString() };
			}
			return new String[] { lowerBound.toString(), upperBound.toString() };
		}

	}

}
