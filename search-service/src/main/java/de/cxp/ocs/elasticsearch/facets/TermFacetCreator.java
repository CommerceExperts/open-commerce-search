package de.cxp.ocs.elasticsearch.facets;

import java.util.*;
import java.util.function.Function;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig.ValueOrder;
import de.cxp.ocs.config.FacetType;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.util.SearchQueryBuilder;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Accessors(chain = true)
public class TermFacetCreator extends NestedFacetCreator {

	@Setter
	private int				maxFacetValues	= 100;
	private final Locale	locale;

	public TermFacetCreator(Map<String, FacetConfig> facetConfigs, Function<String, FacetConfig> defaultFacetConfigProvider, Locale l) {
		super(facetConfigs, defaultFacetConfigProvider);
		this.locale = l;
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
				// value order could be set here, but then would apply for all
				// term facets.
				// Considering the values with the highest count as more
				// relevant than potentially lost alphanumeric values with a low
				// count. Therefore applying the sort order per facet afterwards
				.subAggregation(AggregationBuilders.terms(FACET_IDS_AGG)
						.field(nestedPathPrefix + ".id")
						.size(1));
	}

	@Override
	protected Optional<Facet> createFacet(Terms.Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter facetFilter,
			SearchQueryBuilder linkBuilder) {
		Facet facet = FacetFactory.create(facetConfig, FacetType.TERM);
		if (facetFilter != null && facetFilter instanceof TermResultFilter) {
			facet.setFiltered(true);
			fillFacet(facet, facetNameBucket, (TermResultFilter) facetFilter, facetConfig, linkBuilder);
		}
		else {
			// unfiltered facet
			fillFacet(facet, facetNameBucket, null, facetConfig, linkBuilder);
		}
		
		if (ValueOrder.ALPHANUM_ASC.equals(facetConfig.getValueOrder())) {
			Collections.sort(facet.entries, Comparator.comparing(entry -> entry.key));
		}
		else if (ValueOrder.ALPHANUM_DESC.equals(facetConfig.getValueOrder())) {
			Collections.sort(facet.entries, Comparator.<FacetEntry, String> comparing(entry -> entry.getKey()).reversed());
		}

		return facet.entries.isEmpty() ? Optional.empty() : Optional.of(facet);
	}

	@Override
	public Optional<Facet> mergeFacets(Facet first, Facet second) {
		if (!FacetType.TERM.name().toLowerCase().equals(first.getType())
				|| !first.getType().equals(second.getType())) {
			return Optional.empty();
		}

		Map<String, FacetEntry> facetEntriesByKey = new HashMap<>();
		first.getEntries().forEach(e -> facetEntriesByKey.put(e.key, e));
		for (FacetEntry additionalEntry : second.getEntries()) {
			FacetEntry prevEntry = facetEntriesByKey.get(additionalEntry.key);
			if (prevEntry == null) {
				first.absoluteFacetCoverage += additionalEntry.docCount;
				first.getEntries().add(additionalEntry);
			}
			else {
				if (prevEntry.id != null && additionalEntry.id != null && !prevEntry.id.equals(additionalEntry.id)) {
					log.warn("merging term facets with same values but different IDs ({} and {}) might lead to unexpected results!"
							+ " Consider to reconfigure facet for fields {} and {}", prevEntry.id, additionalEntry.id, first.fieldName, second.fieldName);
				}
				long newDocCount = Math.max(prevEntry.docCount, additionalEntry.docCount);
				first.absoluteFacetCoverage += newDocCount - prevEntry.docCount;
				prevEntry.docCount = newDocCount;
			}
		}

		second.getMeta().forEach(first.getMeta()::putIfAbsent);

		Collections.sort(first.getEntries(), Comparator.comparingLong(FacetEntry::getDocCount).reversed());

		return Optional.of(first);
	}

	private void fillFacet(Facet facet, Bucket facetNameBucket, TermResultFilter facetFilter, FacetConfig facetConfig, SearchQueryBuilder linkBuilder) {
		Terms facetValues = ((Terms) facetNameBucket.getAggregations().get(FACET_VALUES_AGG));
		Set<String> filterValues = facetFilter == null ? Collections.emptySet() : asSet(facetFilter.getValues());
		long absDocCount = 0;
		for (Bucket valueBucket : facetValues.getBuckets()) {

			String facetValue = valueBucket.getKeyAsString();

			String facetValueId = null;
			Terms facetValueAgg = (Terms) valueBucket.getAggregations().get(FACET_IDS_AGG);
			if (facetValueAgg != null && facetValueAgg.getBuckets().size() > 0) {
				facetValueId = facetValueAgg.getBuckets().get(0).getKeyAsString();
			}

			boolean isSelected = false;
			if (facetFilter != null) {
				if (facetFilter.isFilterOnId()) {
					isSelected = filterValues.contains(facetValueId);
				}
				else {
					isSelected = filterValues.contains(facetValue.toLowerCase(locale));
				}

				if (!facetConfig.isMultiSelect() && !facetConfig.isShowUnselectedOptions() && !isSelected) {
					continue;
				}
			}

			long docCount = getDocumentCount(valueBucket);

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

			facet.addEntry(new FacetEntry(facetValue, facetValueId, docCount, link, isSelected));
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
