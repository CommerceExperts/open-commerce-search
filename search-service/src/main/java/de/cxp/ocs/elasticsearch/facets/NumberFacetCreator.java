package de.cxp.ocs.elasticsearch.facets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregator.KeyedFilter;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilters;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram.Bucket;
import org.elasticsearch.search.aggregations.bucket.histogram.HistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

import de.cxp.ocs.config.FacetConfiguration;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
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
public class NumberFacetCreator implements NestedFacetCreator {

	static final String	GENERAL_NUMBER_FACET_AGG	= "_number_facet";
	static final String	FACET_NAMES_AGG				= "_names";
	static final String	FACET_VALUES_AGG			= "_values";

	private final Map<String, FacetConfig> facetsBySourceField = new HashMap<>();

	public NumberFacetCreator(FacetConfiguration facetConf) {
		// TODO: optimize FacetConfiguration object to avoid such index
		// creation!
		facetConf.getFacets().forEach(fc -> facetsBySourceField.put(fc.getSourceField(), fc));
	}

	@Setter
	private int maxFacets = 2;

	@Setter
	private int wishedFacetSize = 5;

	// TODO: fetch statistics from the numeric ranges of each facet value to
	// use proper interval
	@Setter
	private int interval = 5;

	@Setter
	private NestedFacetCountCorrector nestedFacetCorrector = null;

	@Override
	public AbstractAggregationBuilder<?> buildAggregation(FilterContext filters) {
		String nestedPathPrefix = "";
		if (nestedFacetCorrector != null) nestedPathPrefix = nestedFacetCorrector.getNestedPathPrefix();
		nestedPathPrefix += FieldConstants.NUMBER_FACET_DATA;

		// create value aggregation
		HistogramAggregationBuilder valueAggBuilder = AggregationBuilders.histogram(FACET_VALUES_AGG)
				.field(nestedPathPrefix + ".value")
				.interval(interval)
				.minDocCount(1);

		// pass value aggregation to corrector to get the correct document
		// counts
		if (nestedFacetCorrector != null) nestedFacetCorrector.correctValueAggBuilder(valueAggBuilder);

		List<KeyedFilter> facetFilters = NestedFacetCreator.getAggregationFilters(filters, nestedPathPrefix + ".name");

		return AggregationBuilders.nested(GENERAL_NUMBER_FACET_AGG, nestedPathPrefix)
				.subAggregation(
						AggregationBuilders.filters(FILTERED_AGG, facetFilters.toArray(new KeyedFilter[0]))
								.subAggregation(
										AggregationBuilders.terms(FACET_NAMES_AGG)
												.field(nestedPathPrefix + ".name")
												.size(maxFacets)
												.subAggregation(valueAggBuilder)));
	}

	@Override
	public Collection<Facet> createFacets(List<InternalResultFilter> filters, Aggregations aggResult, SearchQueryBuilder linkBuilder) {
		// TODO: optimize SearchParams object to avoid such index creation!
		Map<String, InternalResultFilter> filtersByName = new HashMap<>();
		filters.forEach(p -> filtersByName.put(p.getField(), p));

		ParsedFilters filtersAgg = ((Nested) aggResult.get(GENERAL_NUMBER_FACET_AGG)).getAggregations().get(FILTERED_AGG);
		List<Facet> extractedFacets = new ArrayList<>();
		for (org.elasticsearch.search.aggregations.bucket.filter.Filters.Bucket filterBucket : filtersAgg.getBuckets()) {
			Terms facetNames = filterBucket.getAggregations().get(FACET_NAMES_AGG);
			extractedFacets.addAll(extractIntervalFacets(facetNames, filtersByName, linkBuilder));
		}

		return extractedFacets;
	}

	private List<Facet> extractIntervalFacets(Terms facetNames, Map<String, InternalResultFilter> filtersByName, SearchQueryBuilder linkBuilder) {
		List<Facet> facets = new ArrayList<>();
		for (Terms.Bucket facetNameBucket : facetNames.getBuckets()) {
			String facetName = facetNameBucket.getKeyAsString();

			// XXX: using a dynamic string as source field might be a bad
			// idea for link creation
			// either log warnings when indexing such attributes or map them to
			// some internal URL friendly name
			FacetConfig facetConfig = facetsBySourceField.get(facetName);
			if (facetConfig == null) facetConfig = new FacetConfig(facetName, facetName);

			Facet facet = FacetFactory.create(facetConfig, "interval");

			InternalResultFilter facetFilter = filtersByName.get(facetName);
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
			facets.add(facet);
		}
		return facets;
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

			if (selectedFilter != null && selectedFilter.getLowerBound().equals(value)) {

			}

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
