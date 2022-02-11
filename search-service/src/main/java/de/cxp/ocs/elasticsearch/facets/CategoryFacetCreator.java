package de.cxp.ocs.elasticsearch.facets;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig.ValueOrder;
import de.cxp.ocs.config.FacetType;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.HierarchialFacetEntry;
import de.cxp.ocs.util.SearchQueryBuilder;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Accessors(chain = true)
public class CategoryFacetCreator extends NestedFacetCreator {

	@Setter
	private int maxFacetValues = 250;

	public CategoryFacetCreator(Map<String, FacetConfig> facetConfigs) {
		super(facetConfigs);
	}
	
	@Override
	protected String getNestedPath() {
		return FieldConstants.PATH_FACET_DATA;
	}

	@Override
	protected AggregationBuilder getNestedValueAggregation(String nestedPathPrefix) {
		return AggregationBuilders.terms(FACET_VALUES_AGG)
				.field(FieldConstants.PATH_FACET_DATA + ".value")
				.size(maxFacetValues)
				.subAggregation(AggregationBuilders.terms(FACET_IDS_AGG)
						.field(FieldConstants.PATH_FACET_DATA + ".id")
						.size(1));
	}

	@Override
	protected boolean onlyFetchAggregationsForConfiguredFacets() {
		return true;
	}

	@Override
	protected boolean correctedNestedDocumentCount() {
		return true;
	}

	@Override
	protected boolean isMatchingFilterType(InternalResultFilter internalResultFilter) {
		return internalResultFilter != null && internalResultFilter.getField() != null 
				&& FieldType.CATEGORY.equals(internalResultFilter.getField().getType());
	}

	@Override
	protected Optional<Facet> createFacet(Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter intFacetFilter, SearchQueryBuilder linkBuilder) {
		Terms categoryAgg = facetNameBucket.getAggregations().get(FACET_VALUES_AGG);
		List<? extends Bucket> catBuckets = categoryAgg.getBuckets();
		if (catBuckets.size() == 0) return Optional.empty();

		// let it crash if it's from the wrong type
		TermResultFilter facetFilter = (TermResultFilter) intFacetFilter;

		Facet facet = FacetFactory.create(facetConfig, FacetType.HIERARCHICAL);

		Map<String, HierarchialFacetEntry> entries = new LinkedHashMap<>(catBuckets.size());
		long absDocCount = 0;
		boolean isFiltered = isMatchingFilterType(intFacetFilter);

		for (Bucket categoryBucket : catBuckets) {
			String categoryPath = categoryBucket.getKeyAsString();

			String[] categories = StringUtils.split(categoryPath, '/');
			
			final boolean isSelectedPath;
			if (isFiltered && intFacetFilter != null && intFacetFilter.getValues().length > 0 && categoryPath != null) {

				String[] filterValues = intFacetFilter.getValues();
				String[] filterValuesSplitByCategories = filterValues.length == 1 && filterValues[0].contains("/") 
						? filterValues[0].split("/") 
						: filterValues;
				
				// skip this path, if it is not a child of the selected path
				if (Arrays.stream(filterValuesSplitByCategories).noneMatch(categoryPath::contains)) {
					continue;
				}
				else {
					// only set true, if this is a complete matching path
					isSelectedPath = filterValues.length == 1 ? filterValues[0].equals(categoryPath) : Arrays.stream(filterValues).anyMatch(categoryPath::equals);
				}
			}
			else {
				isSelectedPath = false;
			}
			
			HierarchialFacetEntry lastLevelEntry = entries.computeIfAbsent(categories[0], c -> toFacetEntry(c, categoryPath, facetConfig, linkBuilder, isSelectedPath));
			for (int i = 1; i < categories.length; i++) {
				FacetEntry child = getChildByKey(lastLevelEntry, categories[i]);
				if (child != null) {
					lastLevelEntry = (HierarchialFacetEntry) child;
				}
				else {
					HierarchialFacetEntry newChild = toFacetEntry(categories[i], categoryPath, facetConfig, linkBuilder, isSelectedPath);
					lastLevelEntry.addChild(newChild);
					lastLevelEntry = newChild;
				}
			}
			long docCount = nestedFacetCorrector != null ? nestedFacetCorrector.getCorrectedDocumentCount(categoryBucket) : categoryBucket.getDocCount();
			absDocCount += docCount;
			lastLevelEntry.setDocCount(docCount);
			lastLevelEntry.setPath(categoryPath);

			Terms idsAgg = (Terms) categoryBucket.getAggregations().get(FACET_IDS_AGG);
			if (idsAgg != null && idsAgg.getBuckets().size() > 0) {
				lastLevelEntry.setId(idsAgg.getBuckets().get(0).getKeyAsString());

				if (facetFilter != null && facetFilter.isFilterOnId()) {
					// TODO: support filtering on multiple ids
					lastLevelEntry.setSelected(lastLevelEntry.getId().equals(facetFilter.getSingleValue()));
				}
			}

			// mark the whole path as selected
			if (lastLevelEntry.isSelected()) {
				HierarchialFacetEntry rootChild = entries.get(categories[0]);
				rootChild.setSelected(true);
				for (int i = 1; i < categories.length - 1; i++) {
					FacetEntry child = getChildByKey(rootChild, categories[i]);
					if (child == null) break;
					child.setSelected(true);
					// child becomes next rootChild
					rootChild = (HierarchialFacetEntry) child;
				}
			}
		}
		facet.setAbsoluteFacetCoverage(absDocCount);
		
		entries.values().forEach(facet.getEntries()::add);

		if (ValueOrder.ALPHANUM_ASC.equals(facetConfig.getValueOrder())) {
			Comparator<FacetEntry> ascValueComparator = Comparator.comparing(entry -> entry.key);
			Collections.sort(facet.entries, ascValueComparator);
			facet.entries.forEach(parent -> sortChildren(parent, ascValueComparator));
		}
		else if (ValueOrder.ALPHANUM_DESC.equals(facetConfig.getValueOrder())) {
			Comparator<FacetEntry> descValueComparator = Comparator.<FacetEntry, String> comparing(entry -> entry.getKey()).reversed();
			Collections.sort(facet.entries, descValueComparator);
			facet.entries.forEach(parent -> sortChildren(parent, descValueComparator));
		}
		
		return Optional.of(facet);
	}

	private void sortChildren(FacetEntry parent, Comparator<FacetEntry> valueComparator) {
		if (parent instanceof HierarchialFacetEntry && ((HierarchialFacetEntry)parent).getChildren().size() > 0) {
			Collections.sort(((HierarchialFacetEntry) parent).getChildren(), valueComparator);
			((HierarchialFacetEntry) parent).getChildren().forEach(subParent -> sortChildren(subParent, valueComparator));
		}
	}

	private FacetEntry getChildByKey(HierarchialFacetEntry entry, String childKey) {
		for (FacetEntry e : entry.children) {
			if (childKey.equals(e.getKey())) {
				return e;
			}
		}
		return null;
	}

	private HierarchialFacetEntry toFacetEntry(String value, String categoryPath, FacetConfig facetConfig, SearchQueryBuilder linkBuilder, boolean isSelected) {
		String link;
		if (isSelected) {
			int parentPathEndIndex = categoryPath.lastIndexOf('/');
			if (parentPathEndIndex != -1)
				link = linkBuilder.withFilterAsLink(facetConfig, categoryPath.substring(0, parentPathEndIndex));
			else
				link = linkBuilder.withoutFilterAsLink(facetConfig, categoryPath);
		}
		else {
			link = linkBuilder.withFilterAsLink(facetConfig, categoryPath);
		}
		return new HierarchialFacetEntry(value, null, 0, link, isSelected);
	}

	@Override
	public Optional<Facet> mergeFacets(Facet a, Facet b) {
		log.warn("YAGNI: merging category facets not implemented! Will keep first facet and drop second one!");
		return Optional.of(a);
	}

}
