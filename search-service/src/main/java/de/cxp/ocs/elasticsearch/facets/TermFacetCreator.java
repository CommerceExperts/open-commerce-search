package de.cxp.ocs.elasticsearch.facets;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FacetType;
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

	private final static String CODE_VALUE_AGG = "_code";

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
				.size(maxFacetValues)
				.subAggregation(AggregationBuilders.terms(CODE_VALUE_AGG)
						.field(nestedPathPrefix + ".code")
						.size(1));
	}

	@Override
	protected Optional<Facet> createFacet(Terms.Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter facetFilter,
			SearchQueryBuilder linkBuilder) {
		Facet facet = FacetFactory.create(facetConfig, FacetType.term);
		if (facetFilter != null && facetFilter instanceof TermResultFilter) {
			facet.setFiltered(true);
			fillFacet(facet, facetNameBucket, (TermResultFilter) facetFilter, facetConfig, linkBuilder);
		}
		else {
			// unfiltered facet
			fillFacet(facet, facetNameBucket, null, facetConfig, linkBuilder);
		}

		return facet.entries.isEmpty() ? Optional.empty() : Optional.of(facet);
	}

	private void fillFacet(Facet facet, Bucket facetNameBucket, TermResultFilter facetFilter, FacetConfig facetConfig, SearchQueryBuilder linkBuilder) {
		Terms facetValues = ((Terms) facetNameBucket.getAggregations().get(FACET_VALUES_AGG));
		Set<String> filterValues = asSet(facetFilter.getValues());
		long absDocCount = 0;
		for (Bucket valueBucket : facetValues.getBuckets()) {
			long docCount = getDocumentCount(valueBucket);
			String facetValue = valueBucket.getKeyAsString();

			String facetValueId = null;
			Terms facetValueAgg = (Terms) valueBucket.getAggregations().get(CODE_VALUE_AGG);
			if (facetValueAgg != null && facetValueAgg.getBuckets().size() > 0) {
				facetValueId = facetValueAgg.getBuckets().get(0).getKeyAsString();
			}

			boolean isSelected = false;
			if (facetFilter != null) {
				if (facetFilter.isFilterOnId()) {
					isSelected = filterValues.contains(facetValueId);
				}
				else {
					isSelected = filterValues.contains(facetValue);
				}
			}

			String link;
			if (isSelected) {
				if (facetFilter.isFilterOnId()) {
					link = linkBuilder.withoutFilterAsLink(facetConfig, facetValueId);
				}
				else {
					link = linkBuilder.withoutFilterAsLink(facetConfig, facetValue);
				}
			}
			else {
				if (facetFilter != null && facetFilter.isFilterOnId()) {
					// as soon as we have a single ID filter and we're
					link = linkBuilder.withFilterAsLink(facetConfig, facetValueId);
				}
				else {
					link = linkBuilder.withFilterAsLink(facetConfig, facetValue);
				}
			}

			FacetEntry facetEntry = new FacetEntry(facetValue, facetValueId, docCount, link, isSelected);

			facet.addEntry(facetEntry);
			absDocCount += docCount;
		}
		facet.setAbsoluteFacetCoverage(absDocCount);
	}

	private Set<String> asSet(String[] values) {
		if (values.length == 0) return Collections.emptySet();
		if (values.length == 1) return Collections.singleton(values[0]);

		Set<String> hashedValues = new HashSet<>();
		for (String val : values) {
			hashedValues.add(val);
		}
		return hashedValues;
	}

	private long getDocumentCount(Bucket valueBucket) {
		long docCount = nestedFacetCorrector != null
				? nestedFacetCorrector.getCorrectedDocumentCount(valueBucket)
				: valueBucket.getDocCount();
		return docCount;
	}

}
