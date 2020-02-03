package de.cxp.ocs.preprocessor.util;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import de.cxp.ocs.config.Field;

/**
 * Takes a list of <code>/</code> separated category paths and splits them into
 * separate levels. Also saves all leaf paths.
 */
public class CategorySearchData {

	public static final String	CATEGORY_LEAF_SUFFIX	= "_leaf";
	public static final String	CATEGORY_LVL_SUFFIX		= "_lvl_";
	public static final String	PATH_SEPARATOR			= " ";

	private static final String SLASH = "/";

	final Map<Integer, Set<String>>	categoryLvl;
	final Set<String>				categoryLeaf;

	/**
	 * Creates a new instance and splits the category paths into levels and
	 * leafs.
	 * 
	 * @param paths
	 *        the list of paths to split.
	 */
	public CategorySearchData(final List<String> paths) {
		if (CollectionUtils.isEmpty(paths)) {
			categoryLvl = Collections.emptyMap();
			categoryLeaf = Collections.emptySet();
		}
		else {
			categoryLvl = new LinkedHashMap<>(paths.size());
			categoryLeaf = new LinkedHashSet<>(paths.size());
			processPaths(paths);
		}
	}

	private void processPaths(final List<String> paths) {
		for (int i = 0; i < paths.size(); i++) {
			String path = paths.get(i);
			String[] pathLvls = path.split(SLASH);

			for (int j = 0; j < pathLvls.length; j++) {
				categoryLvl.computeIfAbsent(j, k -> new LinkedHashSet<>()).add(pathLvls[j]);
			}
			categoryLeaf.add(pathLvls[pathLvls.length - 1]);
		}
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
	 *         if the level is <code><0</code> or
	 *         <code>>{@link CategorySearchData#getCategoryLvlDepth()}</code>
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
	 * Converts this instance into a {@link SourceItem}. The name of the
	 * category field is used as prefix, followed by either
	 * <ol>
	 * <li>_lvl_x</li>
	 * <li>_leaf</li>
	 * </ol>
	 * for each level and once for all leafs.
	 * 
	 * @param importItem
	 *        the source item where the data will be stored.
	 * @param categoryField
	 *        the category field which is used to obtain the name of the stored
	 *        fields.
	 */
	public void toSourceItem(final Map<String, Object> sourceData, final Field categoryField) {
		for (int i = 0; i < categoryLvl.size(); i++) {
			sourceData.put(getFieldLevelName(categoryField, i), StringUtils.join(categoryLvl.get(i),
					PATH_SEPARATOR));
		}
		sourceData.put(getFieldLeafName(categoryField), StringUtils.join(categoryLeaf, PATH_SEPARATOR));
	}

	private String getFieldLevelName(final Field categoryField, int i) {
		return new StringBuilder(categoryField.getName()).append(CATEGORY_LVL_SUFFIX).append(i).toString();
	}

	private String getFieldLeafName(final Field categoryField) {
		return categoryField.getName().concat(CATEGORY_LEAF_SUFFIX);
	}
}
