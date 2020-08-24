package de.cxp.ocs.elasticsearch.facets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram.Bucket;
import org.elasticsearch.search.aggregations.bucket.histogram.HistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

import de.cxp.ocs.config.FacetConfiguration;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.util.InternalSearchParams;
import de.cxp.ocs.util.SearchQueryBuilder;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class NumberFacetCreator implements NestedFacetCreator {

	private static String	GENERAL_NUMBER_FACET_AGG	= "_number_facet";
	private static String	FACET_NAMES_AGG				= "_names";
	private static String	FACET_VALUES_AGG			= "_values";

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
	public AbstractAggregationBuilder<?> buildAggregation(InternalSearchParams parameters) {
		// TODO: for multi-select facets, filter facets accordingly

		String nestedPathPrefix = "";
		if (nestedFacetCorrector != null) nestedPathPrefix = nestedFacetCorrector.getNestedPathPrefix();

		HistogramAggregationBuilder valueAggBuilder = AggregationBuilders.histogram(FACET_VALUES_AGG)
				.field(nestedPathPrefix + FieldConstants.NUMBER_FACET_DATA + ".value")
				.interval(interval)
				.minDocCount(1);
		if (nestedFacetCorrector != null) nestedFacetCorrector.correctValueAggBuilder(valueAggBuilder);

		return AggregationBuilders.nested(GENERAL_NUMBER_FACET_AGG, nestedPathPrefix + FieldConstants.NUMBER_FACET_DATA)
				.subAggregation(
						AggregationBuilders.terms(FACET_NAMES_AGG)
								.field(nestedPathPrefix + FieldConstants.NUMBER_FACET_DATA + ".name")
								.size(maxFacets)
								.subAggregation(valueAggBuilder));
	}

	@Override
	public Collection<Facet> createFacets(List<InternalResultFilter> filters, Aggregations aggResult, SearchQueryBuilder linkBuilder) {
		Terms facetNames = ((Nested) aggResult.get(GENERAL_NUMBER_FACET_AGG))
				.getAggregations().get(FACET_NAMES_AGG);

		// TODO: optimize SearchParams object to avoid such index creation!
		Map<String, InternalResultFilter> filtersByName = new HashMap<>();
		filters.forEach(p -> filtersByName.put(p.getField(), p));

		List<Facet> termFacets = new ArrayList<>();
		for (Terms.Bucket facetNameBucket : facetNames.getBuckets()) {
			String facetName = facetNameBucket.getKeyAsString();

			// XXX: using a dynamic string as source field might be a bad
			// idea for link creation
			// either log warnings when indexing such attributes or map them to
			// some internal URL friendly name
			FacetConfig facetConfig = facetsBySourceField.get(facetName);
			if (facetConfig == null) facetConfig = new FacetConfig(facetName, facetName);

			// TODO: add support for range facet
			// TODO: change number facets to have "min/max" entries instead formatted labels
			Facet facet = FacetFactory.create(facetConfig);

			InternalResultFilter facetFilter = filtersByName.get(facetName);
			if (facetFilter != null && facetFilter instanceof NumberResultFilter) {
				if (!facetConfig.isMultiSelect()) {
					// filtered single select facet
					long docCount = getDocCount(facetNameBucket);
					String facetEntryLabel = new NumericFacetEntryBuilder((NumberResultFilter) facetFilter).getEntry();
					facet.addEntry(
							facetEntryLabel,
							docCount,
							linkBuilder.withoutFilterAsLink(facetConfig, facetEntryLabel));
					facet.setAbsoluteFacetCoverage(docCount);
				}
				else {
					// multiselect facet
					fillFacet(facetNameBucket, facet, facetConfig, linkBuilder);
				}
			}
			else {
				// unfiltered facet
				fillFacet(facetNameBucket, facet, facetConfig, linkBuilder);
			}
			termFacets.add(facet);
		}

		return termFacets;
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

	private void fillFacet(Terms.Bucket facetNameBucket, Facet facet, FacetConfig facetConfig, SearchQueryBuilder linkBuilder) {
		Histogram facetValues = ((Histogram) facetNameBucket.getAggregations().get(FACET_VALUES_AGG));
		List<? extends Bucket> valueBuckets = facetValues.getBuckets();

		long variantCount = facetNameBucket.getDocCount();
		long variantCountPerBucket = variantCount / (wishedFacetSize + 1);

		NumericFacetEntryBuilder currentValueInterval = new NumericFacetEntryBuilder();
		int currentDocumentCount = 0;
		int currentVariantCount = 0;
		long absDocCount = 0;
		for (Histogram.Bucket valueBucket : valueBuckets) {
			if (currentDocumentCount == 0) {
				currentValueInterval.lowerBound = (Double) valueBucket.getKey();
			}

			long docCount = nestedFacetCorrector != null
					? nestedFacetCorrector.getCorrectedDocumentCount(valueBucket)
					: valueBucket.getDocCount();
			currentVariantCount += valueBucket.getDocCount();
			currentDocumentCount += docCount;
			absDocCount += docCount;

			if (currentVariantCount >= variantCountPerBucket) {
				currentValueInterval.upperBound = (Double) valueBucket.getKey() + interval - 0.01;
				facet.addEntry(
						// TODO: differentiate between label and filter value
						currentValueInterval.getEntry(),
						currentDocumentCount,
						// FIXME: use parameter-builder or similar to build filter values
						// FIXME: mark selected elements and create deselect link!
						linkBuilder.withFilterAsLink(facetConfig, currentValueInterval.lowerBound+","+currentValueInterval.upperBound));

				currentDocumentCount = 0;
				currentVariantCount = 0;
				currentValueInterval = new NumericFacetEntryBuilder();
			}
		}
		facet.setAbsoluteFacetCoverage(absDocCount);
	}

	@NoArgsConstructor
	private static class NumericFacetEntryBuilder {

		Double	lowerBound;
		Double	upperBound;

		NumericFacetEntryBuilder(NumberResultFilter facetFilter) {
			Number lowerBoundValue = facetFilter.getLowerBound();
			Number upperBoundValue = facetFilter.getUpperBound();
			lowerBound = lowerBoundValue == null ? null : lowerBoundValue.doubleValue();
			upperBound = upperBoundValue == null ? null : upperBoundValue.doubleValue();
		}

		String getEntry() {
			// TODO handle no lower bound / no upper bound
			// TODO replace hard coded locale by configuration entry, as soon as
			// we have a per channel conf
			return String.format(Locale.GERMAN, "%1$,.2f", lowerBound) + " - " + String.format(Locale.GERMAN, "%1$,.2f",
					upperBound);
		}

		@Override
		public String toString() {
			return getEntry();
		}
	}

}
