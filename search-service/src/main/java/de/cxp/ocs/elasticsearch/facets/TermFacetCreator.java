package de.cxp.ocs.elasticsearch.facets;

import java.util.Map;
import java.util.Optional;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.util.SearchQueryBuilder;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class TermFacetCreator extends NestedFacetCreator {

	@Setter
	private int maxFacetValues = 100;

	public TermFacetCreator(Map<String, FacetConfig> facetConfigs) {
		super(facetConfigs);
	}

	@Override
	protected String getNestedPath() {
		return FieldConstants.TERM_FACET_DATA;
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
	protected boolean isMatchingFilterType(InternalResultFilter internalResultFilter) {
		return internalResultFilter instanceof TermResultFilter;
	}

	@Override
	protected AggregationBuilder getNestedValueAggregation(String nestedPathPrefix) {
		return AggregationBuilders.terms(FACET_VALUES_AGG)
				.field(nestedPathPrefix + ".value")
				.size(maxFacetValues);
	}

	@Override
	protected Optional<Facet> createFacet(Terms.Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter facetFilter,
			SearchQueryBuilder linkBuilder) {
		Facet facet = FacetFactory.create(facetConfig, "text");
		if (facetFilter != null && facetFilter instanceof TermResultFilter) {
			facet.setFiltered(true);
			if (facetConfig.isMultiSelect() || facetConfig.isShowUnselectedOptions()) {
				fillFacet(facet, facetNameBucket, facetConfig, linkBuilder);
			}
			else {
				fillSingleSelectFacet(facetNameBucket, facet, (TermResultFilter) facetFilter, facetConfig, linkBuilder);
			}
		}
		else {
			// unfiltered facet
			fillFacet(facet, facetNameBucket, facetConfig, linkBuilder);
		}

		return facet.entries.isEmpty() ? Optional.empty() : Optional.of(facet);
	}

	private void fillSingleSelectFacet(Bucket facetNameBucket, Facet facet, TermResultFilter facetFilter, FacetConfig facetConfig,
			SearchQueryBuilder linkBuilder) {
		Terms facetValues = ((Terms) facetNameBucket.getAggregations().get(FACET_VALUES_AGG));
		long absDocCount = 0;
		for (String filterValue : facetFilter.getValues()) {
			Bucket elementBucket = facetValues.getBucketByKey(filterValue);
			if (elementBucket != null) {
				long docCount = getDocumentCount(elementBucket);
				facet.addEntry(buildFacetEntry(facetConfig, filterValue, docCount, linkBuilder));
				absDocCount += docCount;
			}
		}
		facet.setAbsoluteFacetCoverage(absDocCount);
	}

	private void fillFacet(Facet facet, Bucket facetNameBucket, FacetConfig facetConfig, SearchQueryBuilder linkBuilder) {
		Terms facetValues = ((Terms) facetNameBucket.getAggregations().get(FACET_VALUES_AGG));
		long absDocCount = 0;
		for (Bucket valueBucket : facetValues.getBuckets()) {
			long docCount = getDocumentCount(valueBucket);
			facet.addEntry(buildFacetEntry(facetConfig, valueBucket.getKeyAsString(), docCount, linkBuilder));
			absDocCount += docCount;
		}
		facet.setAbsoluteFacetCoverage(absDocCount);
	}

	private FacetEntry buildFacetEntry(FacetConfig facetConfig, String filterValue, long docCount, SearchQueryBuilder linkBuilder) {
		boolean isSelected = linkBuilder.isFilterSelected(facetConfig, filterValue);
		return new FacetEntry(
				filterValue,
				null, // TODO: fetch IDS
				docCount,
				isSelected ? linkBuilder.withoutFilterAsLink(facetConfig, filterValue) : linkBuilder.withFilterAsLink(facetConfig, filterValue),
				isSelected);
	}

	private long getDocumentCount(Bucket valueBucket) {
		long docCount = nestedFacetCorrector != null
				? nestedFacetCorrector.getCorrectedDocumentCount(valueBucket)
				: valueBucket.getDocCount();
		return docCount;
	}

}
