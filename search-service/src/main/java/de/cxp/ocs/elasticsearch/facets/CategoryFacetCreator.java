package de.cxp.ocs.elasticsearch.facets;

import static de.cxp.ocs.elasticsearch.query.filter.PathResultFilter.PATH_SEPARATOR;

import java.util.*;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;

import de.cxp.ocs.config.*;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.PathResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.HierarchialFacetEntry;
import de.cxp.ocs.util.DefaultLinkBuilder;
import de.cxp.ocs.util.FacetEntrySorter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Accessors(chain = true)
public class CategoryFacetCreator extends NestedFacetCreator {

	@Setter
	private int maxFacetValues = 250;
	private final Map<String, FacetEntrySorter>	facetSorters	= new HashMap<>();
	private final boolean						isExplicitFacetCreator;

	public CategoryFacetCreator(Map<String, FacetConfig> facetConfigs, Function<String, FacetConfig> defaultFacetConfigProvider) {
		this(facetConfigs, defaultFacetConfigProvider, false);
	}

	public CategoryFacetCreator(Map<String, FacetConfig> facetConfigs, Function<String, FacetConfig> defaultFacetConfigProvider, boolean isExplicitFacetCreator) {
		super(facetConfigs, defaultFacetConfigProvider == null ? name -> new FacetConfig(name, name).setType(FacetType.HIERARCHICAL.name()) : defaultFacetConfigProvider);
		this.isExplicitFacetCreator = isExplicitFacetCreator;
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
		return isExplicitFacetCreator;
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
	protected Optional<Facet> createFacet(Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter intFacetFilter, DefaultLinkBuilder linkBuilder) {
		Terms categoryAgg = facetNameBucket.getAggregations().get(FACET_VALUES_AGG);
		List<? extends Bucket> catBuckets = categoryAgg.getBuckets();
		if (catBuckets.isEmpty()) return Optional.empty();

		// let it crash if it's from the wrong type
		PathResultFilter facetFilter = intFacetFilter != null && intFacetFilter.isNegated() ? null : (PathResultFilter) intFacetFilter;
		Facet facet = FacetFactory.create(facetConfig, FacetType.HIERARCHICAL);

		// map of every path to its full id-path
		Set<String> selectedPaths = facetFilter == null || facetFilter.getValues().length == 0 ? Collections.emptySet() : new HashSet<>(facetFilter.getValues().length);
		CategoryContext context = new CategoryContext(catBuckets.size());
		context.facetConfig = facetConfig;
		context.linkBuilder = linkBuilder;
		context.facetFilter = facetFilter;

		long absDocCount = 0;
		boolean isFiltered = isMatchingFilterType(facetFilter);

		for (Bucket categoryBucket : catBuckets) {
			CategoryExtract category = extractCategoryData(categoryBucket, context.idPathIndex);

			if (isFiltered && isSelectedPath(category, facetFilter)) {
				category.isSelectedPath = Boolean.TRUE;
				selectedPaths.add(category.pathString);
			}

			long docCount = nestedFacetCorrector != null ? nestedFacetCorrector.getCorrectedDocumentCount(categoryBucket) : categoryBucket.getDocCount();
			absDocCount += docCount;

			HierarchialFacetEntry lastLevelEntry = createFacetEntryInHierarchy(category, context);
			lastLevelEntry.setDocCount(docCount);
			lastLevelEntry.setPath(category.pathString);
			lastLevelEntry.setId(category.id);
		}
		facet.setAbsoluteFacetCoverage(absDocCount);

		if (!facetConfig.isShowUnselectedOptions() && isFiltered) {
			// if unselected options should not be shown,
			// this removes all siblings of selected elements.
			copyOnlySelectedPaths(context.entries.values(), facet, selectedPaths);
		}
		else {
			// copy all entries into facet
			context.entries.values().forEach(facet.getEntries()::add);
		}

		getFacetSorter(facetConfig, facetFilter == null ? null : facetFilter.getField()).sort(facet);

		return Optional.of(facet);
	}

	private FacetEntrySorter getFacetSorter(FacetConfig facetConfig, Field field) {
		return facetSorters.computeIfAbsent(facetConfig.getSourceField(), fieldName -> {
			int estimatedFacetValues = field instanceof IndexedField ? ((IndexedField) field).getValueCardinality() : maxFacetValues;
			return FacetEntrySorter.of(facetConfig.getValueOrder(), estimatedFacetValues);
		});
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
		if (idBuckets != null && !idBuckets.isEmpty()) {
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
		int parentPathEndIndex = categoryPath.lastIndexOf(PATH_SEPARATOR);
		if (parentPathEndIndex >= 0) {
			parentIdPath = idPathIndex.get(categoryPath.substring(0, parentPathEndIndex));
		}

		String idPath;
		if (parentIdPath != null) {
			idPath = parentIdPath + PATH_SEPARATOR + categoryId;
		}
		else {
			idPath = PATH_SEPARATOR + categoryId;
		}
		idPathIndex.put(categoryPath, idPath);
		return idPath;
	}

	private boolean isSelectedPath(final CategoryExtract category, final PathResultFilter facetFilter) {
		boolean isSelectedPath = false;
		if (facetFilter.getValues().length > 0) {
			if (facetFilter.isFilterOnId()) {
				for (String idFilter : facetFilter.getValues()) {
					if (category != null) {
						if ((idFilter.charAt(0) == PATH_SEPARATOR && category.idPathString != null && category.idPathString.equals(idFilter))
								|| (category.id != null && category.id.equals(idFilter))) {
							isSelectedPath = true;
							break;
						}
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

	private HierarchialFacetEntry createFacetEntryInHierarchy(CategoryExtract category, CategoryContext context) {
		// make sure the whole path exists as a nested HierarchialFacetEntry,
		// each element of the path being a HierarchialFacetEntr
		HierarchialFacetEntry lastLevelEntry = context.entries.computeIfAbsent(category.path[0], c -> toFacetEntry(0, category, context));
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
				HierarchialFacetEntry newChild = toFacetEntry(i, category, context);
				lastLevelEntry.addChild(newChild);
				lastLevelEntry = newChild;
			}
		}
		return lastLevelEntry;
	}

	/**
	 * Create the facet entry for the category defined by the categoryPathIndex that refers to the according path of the
	 * given CategoryExtract. The documentCount will be corrected afterwards.
	 * 
	 * @param categoryPathIndex
	 *        the index of the category inside the path for which a link should be created.
	 * @param category
	 *        the current full category. The actual category could be a parent of it.
	 * @param context
	 *        category context will all objects around this facet.
	 * @return the created facet entry
	 */
	protected HierarchialFacetEntry toFacetEntry(final int categoryPathIndex, final CategoryExtract category, final CategoryContext context) {
		String categoryName = category.path[categoryPathIndex];
		String link = createLink(categoryPathIndex, category, context);
		return new HierarchialFacetEntry(categoryName, null, 0, link, category.isSelectedPath);
	}

	/**
	 * Creates a link (using the context.linkBuilder) for the category defined by the categoryPathIndex that refers to
	 * the according path of the current category.
	 * 
	 * <p>
	 * The logic depends on the different settings and active filters:
	 * </p>
	 * 
	 * <p>
	 * If "multi-select" is enabled, the filters unrelated to this path stay as is. The current path is then just
	 * appended as filter value. This is also the case for "siblings" of a selected path. This means sibling categories
	 * have a classic "multi-select behaviour".<br>
	 * Parent elements of a selected path get a link that replaces the child filter by that parent filter. That means
	 * choosing a parent facet entry / filter, will remove the child-filter and select the parent.<br>
	 * Same for child elements of a selected path: the active filter is replaced by that child path, which on the
	 * contrary to parent filters will reduce the result size.
	 * </p>
	 * <p>
	 * In case "multi-select" is <strong>disabled</strong>, each facet element will get a link that selects only that
	 * filter and removes other potential filters of that facet.<br>
	 * Selected facet elements will receive a filter that removes all facet-filters completely.
	 * </p>
	 * 
	 * @param categoryPathIndex
	 *        the index of the category inside the path for which a link should be created.
	 * @param category
	 *        the current full category. The actual category could be a parent of it.
	 * @param context
	 *        category context will all objects around this facet.
	 * @return the link as URL query part (e.g. "category.id=123&amp;q=foo")
	 */
	protected String createLink(final int categoryPathIndex, final CategoryExtract category, final CategoryContext context) {
		String link;
		String pathFilterValue;
		if (context.facetFilter != null && context.facetFilter.isFilterOnId()) {
			pathFilterValue = PATH_SEPARATOR + joinPartialPath(category.idPath, categoryPathIndex);
		}
		else {
			pathFilterValue = joinPartialPath(category.path, categoryPathIndex);
		}

		String[] filterValues;
		if (context.facetFilter != null && context.facetConfig.isMultiSelect()) {
			// if a filter is already set that includes that path, we have to check if that specific path is a
			// parent path or exact that category path.
			// In case it is a parent path, that parent path can be selected to unselect the child path.
			// In case this exact category is selected already, we want that filter-value removed completely
			Set<String> filterValuesSet = new HashSet<>(context.facetFilter.getValues().length);
			for (String value : context.facetFilter.getValues()) {
				if (value.equals(pathFilterValue)) {
					// skip
				}
				else if (context.facetFilter.isFilterOnId() && value.equals(category.idPath[categoryPathIndex])) {
					// skip special case: a filter on single ID without the full path
				}
				else if (value.startsWith(pathFilterValue) || pathFilterValue.startsWith(value)) {
					// replace old value with current subPath
					filterValuesSet.add(pathFilterValue);
				}
				else {
					filterValuesSet.add(value);
				}
			}
			if (!category.isSelectedPath) filterValuesSet.add(pathFilterValue);
			filterValues = filterValuesSet.isEmpty() ? null : filterValuesSet.toArray(new String[filterValuesSet.size()]);
		}
		else if (category.isSelectedPath) {
			filterValues = null;
		}
		else {
			filterValues = new String[] { pathFilterValue };
		}
		link = filterValues == null ? context.linkBuilder.withoutFilterAsLink(context.facetConfig) : context.linkBuilder.withExactFilterAsLink(context.facetConfig, filterValues);
		return link;
	}

	protected String joinPartialPath(String[] pathValues, int endIndex) {
		if (endIndex < 0 || endIndex >= pathValues.length) throw new IndexOutOfBoundsException("no pathValues for index " + endIndex);
		if (endIndex == 0) return pathValues[0];
		return StringUtils.join(pathValues, PATH_SEPARATOR, 0, endIndex + 1);
	}

	private FacetEntry getChildByKey(HierarchialFacetEntry entry, String childKey) {
		for (FacetEntry e : entry.children) {
			if (childKey.equals(e.getKey())) {
				return e;
			}
		}
		return null;
	}

	private void copyOnlySelectedPaths(Collection<HierarchialFacetEntry> rootEntries, Facet facet, Set<String> selectedPaths) {
		for (HierarchialFacetEntry rootEntry : rootEntries) {
			if (rootEntry.isSelected()) {
				if (!selectedPaths.contains(rootEntry.getPath())) {
					removeUnselectedChildren(selectedPaths, rootEntry);
				}
				facet.getEntries().add(rootEntry);
			}
			// unselected root-entries are skipped/removed
		}
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

	@Override
	public Optional<Facet> mergeFacets(Facet a, Facet b) {
		log.warn("YAGNI: merging category facets not implemented! Will keep first facet and drop second one!");
		return Optional.of(a);
	}

	@ToString
	protected static class CategoryExtract {

		final String	pathString;
		final String[]	path;
		final String	id;
		final String	idPathString;
		final String[]	idPath;
		boolean			isSelectedPath;

		public CategoryExtract(final String categoryPathStr, final String categoryId, String idPathStr) {
			pathString = categoryPathStr;
			id = categoryId;
			idPathString = idPathStr;
			path = StringUtils.split(categoryPathStr, PATH_SEPARATOR);
			idPath = StringUtils.split(idPathStr, PATH_SEPARATOR);
		}
	}

	protected static class CategoryContext {

		PathResultFilter							facetFilter;
		DefaultLinkBuilder							linkBuilder;
		FacetConfig									facetConfig;
		final Map<String, String>					idPathIndex;
		final Map<String, HierarchialFacetEntry>	entries;

		public CategoryContext(int expectedEntriesSize) {
			idPathIndex = new HashMap<String, String>(expectedEntriesSize);
			entries = new LinkedHashMap<>(expectedEntriesSize);
		}
	}
}
