package de.cxp.ocs.elasticsearch.facets;

import java.util.*;
import java.util.function.Function;

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
import de.cxp.ocs.elasticsearch.query.filter.PathResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.HierarchialFacetEntry;
import de.cxp.ocs.util.SearchQueryBuilder;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Accessors(chain = true)
public class CategoryFacetCreator extends NestedFacetCreator {

	@Setter
	private int maxFacetValues = 250;

	public CategoryFacetCreator(Map<String, FacetConfig> facetConfigs, Function<String, FacetConfig> defaultFacetConfigProvider) {
		super(facetConfigs, defaultFacetConfigProvider);
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
		PathResultFilter facetFilter = (PathResultFilter) intFacetFilter;
		Facet facet = FacetFactory.create(facetConfig, FacetType.HIERARCHICAL);

		// map of every path to its full id-path
		Map<String, String> idPathIndex = new HashMap<String, String>(catBuckets.size());
		Set<String> selectedPaths = new HashSet<>(facetFilter.getValuesAsList().size());
		Map<String, HierarchialFacetEntry> entries = new LinkedHashMap<>(catBuckets.size());

		long absDocCount = 0;
		boolean isFiltered = isMatchingFilterType(facetFilter);

		for (Bucket categoryBucket : catBuckets) {
			CategoryExtract category = extractCategoryData(categoryBucket, idPathIndex);

			if (isFiltered && isSelectedPath(category, facetFilter)) {
				category.isSelectedPath = Boolean.TRUE;
				selectedPaths.add(category.pathString);
			}

			long docCount = nestedFacetCorrector != null ? nestedFacetCorrector.getCorrectedDocumentCount(categoryBucket) : categoryBucket.getDocCount();
			absDocCount += docCount;

			HierarchialFacetEntry lastLevelEntry = createFacetEntryInHierarchy(category, entries, facetConfig, linkBuilder);
			lastLevelEntry.setDocCount(docCount);
			lastLevelEntry.setPath(category.pathString);
			lastLevelEntry.setId(category.id);
		}
		facet.setAbsoluteFacetCoverage(absDocCount);

		// copy relevant entries into facet
		for (HierarchialFacetEntry rootEntry : entries.values()) {
			if (!facetConfig.isShowUnselectedOptions() && isFiltered) {
				if (rootEntry.isSelected()) {
					removeUnselectedChildren(selectedPaths, rootEntry);
					facet.getEntries().add(rootEntry);
				}
				// unselected paths are skipped/removed
			}
			else {
				facet.getEntries().add(rootEntry);
			}
		}

		sortFacetEntries(facetConfig, facet);

		return Optional.of(facet);
	}

	private CategoryExtract extractCategoryData(Bucket categoryBucket, Map<String, String> idPathIndex) {
		final String categoryPath = categoryBucket.getKeyAsString();
		final String categoryId = extractCategoryId(categoryBucket);
		final String idPath = getIdPath(categoryPath, categoryId, idPathIndex);
		return new CategoryExtract(categoryPath, categoryId, idPath);
	}

	private String extractCategoryId(Bucket categoryBucket) {
		Terms idsAgg = (Terms) categoryBucket.getAggregations().get(FACET_IDS_AGG);
		List<? extends Bucket> idBuckets = idsAgg.getBuckets();
		String id = null;
		if (idBuckets != null && idBuckets.size() > 0) {
			id = idBuckets.iterator().next().getKeyAsString();
			if (idBuckets.size() > 1) {
				log.warn("Unexpected category bucket: More than one ID for the same category name can't be handled (yet)."
						+ " Will just use the first ID! id={} for bucket key={}", id, categoryBucket.getKeyAsString());
			}
		}
		return id;
	}

	private String getIdPath(String categoryPath, final String categoryId, Map<String, String> idPathIndex) {
		String parentIdPath = null;
		int parentPathEndIndex = categoryPath.lastIndexOf(PathResultFilter.PATH_SEPARATOR);
		if (parentPathEndIndex >= 0) {
			parentIdPath = idPathIndex.get(categoryPath.substring(0, parentPathEndIndex));
		}

		String idPath;
		if (parentIdPath != null) {
			idPath = parentIdPath + PathResultFilter.PATH_SEPARATOR + categoryId;
		}
		else {
			idPath = PathResultFilter.PATH_SEPARATOR + categoryId;
		}
		idPathIndex.put(categoryPath, idPath);
		return idPath;
	}

	private boolean isSelectedPath(final CategoryExtract category, final PathResultFilter facetFilter) {
		boolean isSelectedPath = false;
		if (facetFilter.getValues().length > 0) {
			if (facetFilter.isFilterOnId()) {
				for (String idFilter : facetFilter.getValuesAsList()) {
					if ((idFilter.charAt(0) == PathResultFilter.PATH_SEPARATOR && category.idPath.equals(idFilter))
							|| category.id.equals(idFilter)) {
						isSelectedPath = true;
						break;
					}
				}
			}
			else {
				// only set true, if this is a complete matching path
				String[] filterValues = facetFilter.getValues();
				isSelectedPath = filterValues.length == 1 ? filterValues[0].equals(category.pathString)
						: Arrays.stream(filterValues).anyMatch(category.pathString::equals);
			}
		}
		return isSelectedPath;
	}

	private HierarchialFacetEntry createFacetEntryInHierarchy(CategoryExtract category, Map<String, HierarchialFacetEntry> entries, FacetConfig facetConfig,
			SearchQueryBuilder linkBuilder) {
		// make sure the whole path exists as a nested HierarchialFacetEntry,
		// each element of the path being a HierarchialFacetEntry
		HierarchialFacetEntry lastLevelEntry = entries.computeIfAbsent(category.path[0], c -> toFacetEntry(c, category, facetConfig, linkBuilder));
		for (int i = 1; i < category.path.length; i++) {
			// mark the whole path as selected if a child is selected
			if (category.isSelectedPath) {
				lastLevelEntry.setSelected(Boolean.TRUE);
			}

			FacetEntry child = getChildByKey(lastLevelEntry, category.path[i]);
			if (child != null) {
				lastLevelEntry = (HierarchialFacetEntry) child;
			}
			else {
				HierarchialFacetEntry newChild = toFacetEntry(category.path[i], category, facetConfig, linkBuilder);
				lastLevelEntry.addChild(newChild);
				lastLevelEntry = newChild;
			}
		}
		return lastLevelEntry;
	}

	private HierarchialFacetEntry toFacetEntry(String value, CategoryExtract categoryContext, FacetConfig facetConfig, SearchQueryBuilder linkBuilder) {
		String link;
		boolean isSelected = categoryContext.isSelectedPath;
		String categoryPath = categoryContext.pathString;
		if (isSelected) {
			int unselectPathEndIndex = categoryPath.lastIndexOf('/');
			if (unselectPathEndIndex == -1) {
				link = linkBuilder.withoutFilterAsLink(facetConfig, categoryPath);
			}
			else if (categoryPath.endsWith(value)) {
				link = linkBuilder.withFilterAsLink(facetConfig, categoryPath.substring(0, unselectPathEndIndex));
			}
			else {
				unselectPathEndIndex = categoryPath.lastIndexOf(value + "/") + value.length();
				link = linkBuilder.withFilterAsLink(facetConfig, categoryPath.substring(0, unselectPathEndIndex));
			}

		}
		else {
			link = linkBuilder.withFilterAsLink(facetConfig, categoryPath);
		}
		return new HierarchialFacetEntry(value, null, 0, link, isSelected);
	}

	private FacetEntry getChildByKey(HierarchialFacetEntry entry, String childKey) {
		for (FacetEntry e : entry.children) {
			if (childKey.equals(e.getKey())) {
				return e;
			}
		}
		return null;
	}

	private void removeUnselectedChildren(Set<String> selectedPaths, HierarchialFacetEntry rootEntry) {
		Iterator<FacetEntry> childIterator = rootEntry.getChildren().iterator();
		while (childIterator.hasNext()) {
			HierarchialFacetEntry child = (HierarchialFacetEntry) childIterator.next();
			if (!child.isSelected()) {
				childIterator.remove();
			}
			else if (!selectedPaths.contains(child.getPath())) {
				removeUnselectedChildren(selectedPaths, child);
			}
		}
	}

	private void sortFacetEntries(FacetConfig facetConfig, Facet facet) {
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
	}

	private void sortChildren(FacetEntry parent, Comparator<FacetEntry> valueComparator) {
		if (parent instanceof HierarchialFacetEntry && ((HierarchialFacetEntry) parent).getChildren().size() > 0) {
			Collections.sort(((HierarchialFacetEntry) parent).getChildren(), valueComparator);
			((HierarchialFacetEntry) parent).getChildren().forEach(subParent -> sortChildren(subParent, valueComparator));
		}
	}

	@Override
	public Optional<Facet> mergeFacets(Facet a, Facet b) {
		log.warn("YAGNI: merging category facets not implemented! Will keep first facet and drop second one!");
		return Optional.of(a);
	}

	@ToString
	private static class CategoryExtract {

		final String	pathString;
		final String[]	path;
		final String	id;
		final String	idPath;
		boolean			isSelectedPath;

		public CategoryExtract(final String categoryPath, final String categoryId, String idPath) {
			this.pathString = categoryPath;
			this.id = categoryId;
			this.idPath = idPath;
			path = StringUtils.split(categoryPath, PathResultFilter.PATH_SEPARATOR);
		}
	}
}
