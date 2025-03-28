package de.cxp.ocs.preprocessor.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Takes a list of <code>/</code> separated category paths and splits them into
 * separate levels. Also saves all leaf paths.
 */
public class CategorySearchData {

	public static final String	CATEGORY_LEAF_SUFFIX	= "_leaf";
	public static final String	CATEGORY_LVL_SUFFIX		= "_lvl_";
	public static final String	PATH_SEPARATOR			= " ";

	final Map<Integer, Set<String>>	categoryLvl;
	final Set<String>				categoryLeaf;

	/**
	 * Creates a new instance and splits the category paths into levels and
	 * leafs.
	 */
	public CategorySearchData() {
		categoryLvl = new LinkedHashMap<>();
		categoryLeaf = new LinkedHashSet<>();
	}

	public void add(Collection<String[]> categoryPaths) {
		categoryPaths.forEach(this::add);
	}

	private void add(String[] pathLvls) {
		for (int j = 0; j < pathLvls.length; j++) {
			categoryLvl.computeIfAbsent(j, k -> new LinkedHashSet<>()).add(pathLvls[j]);
		}
		categoryLeaf.add(pathLvls[pathLvls.length - 1]);
	}

	/**
	 * Returns the number of the deepest category level from the path list.
	 * 
	 * @return the category depth.
	 */
	public int getCategoryLvlDepth() {
		return categoryLvl.size();
	}

	/**
	 * Returns a category by level.
	 *
	 * @param lvl
	 *        the level of the category to return.
	 * @return the category.
	 *
	 * @throws IndexOutOfBoundsException
	 *         if the level is lower than 0 or greater than
	 *         <code>{@link CategorySearchData#getCategoryLvlDepth()}</code>
	 */
	public Collection<String> getCategory(int lvl) {
		if (lvl < 0 || lvl > getCategoryLvlDepth()) {
			throw new IndexOutOfBoundsException();
		}
		return categoryLvl.get(lvl);
	}

	/**
	 * Returns the leaf entries of each path.
	 *
	 * @return the leaf entries.
	 */
	public Collection<String> getCategoryLeaf() {
		return categoryLeaf;
	}

	/**
	 * Copies the data of this instance into the sourceData map. The name of the
	 * category field is used as prefix, followed by either
	 * <ol>
	 * <li>_lvl_x</li>
	 * <li>_leaf</li>
	 * </ol>
	 * for each level and once for all leafs.
	 * 
	 * @param sourceData
	 *        the source item where the data will be stored.
	 * @param categoryFieldName
	 *        the category field which is used to obtain the name of the stored
	 *        fields.
	 */
	public void toSourceItem(final Map<String, Object> sourceData, final String categoryFieldName) {
		for (int i = 0; i < categoryLvl.size(); i++) {
			sourceData.put(getFieldLevelName(categoryFieldName, i), StringUtils.join(categoryLvl.get(i),
					PATH_SEPARATOR));
		}
		sourceData.put(getFieldLeafName(categoryFieldName), StringUtils.join(categoryLeaf, PATH_SEPARATOR));
	}

	private String getFieldLevelName(final String categoryFieldName, int i) {
		return categoryFieldName + CATEGORY_LVL_SUFFIX + i;
	}

	private String getFieldLeafName(final String categoryFieldName) {
		return categoryFieldName.concat(CATEGORY_LEAF_SUFFIX);
	}
}
