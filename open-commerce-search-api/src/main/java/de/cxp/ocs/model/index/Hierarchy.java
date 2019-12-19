package de.cxp.ocs.model.index;

import lombok.Data;
import lombok.NonNull;

/**
 * Special value type to represent hierarchical data like categories.
 */
@Data
public class Hierarchy {

	public Hierarchy(HierarchyLevel[] levels) {
		if (levels == null || levels.length == 0) {
			throw new IllegalArgumentException("at least one hierarchy level required!");
		}
		this.levels = levels;
	}

	@NonNull
	final HierarchyLevel[] levels;

	/**
	 * Creates a simple path using hierarchy levels without IDs.
	 * 
	 * @param firstHierarchyLevel
	 * @param hierarchyLevels
	 * @return
	 */
	public static Hierarchy simplePath(@NonNull String firstHierarchyLevel, String... hierarchyLevels) {
		HierarchyLevel[] categories = new HierarchyLevel[hierarchyLevels.length + 1];
		categories[0] = new HierarchyLevel(firstHierarchyLevel);
		for (int i = 0; i < hierarchyLevels.length; i++) {
			categories[i + 1] = new HierarchyLevel(hierarchyLevels[i]);
		}
		return new Hierarchy(categories);
	}
}
