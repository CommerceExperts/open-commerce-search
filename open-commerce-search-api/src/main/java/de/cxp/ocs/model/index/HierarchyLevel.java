package de.cxp.ocs.model.index;

import lombok.Data;

/**
 * Category may contain an ID, which can be used for filtering without requiring
 * a filtering based on Strings. The names are used to produce nice facets.
 */
@Data
public class HierarchyLevel {

	public HierarchyLevel(String name) {
		if (name == null || name.isEmpty()) {
			throw new NullPointerException("name can't be null or empty!");
		}
		this.id = null;
		this.name = name;
	}

	/**
	 * if using this constructor, the ID is validated to be not null and not
	 * empty! In case ID is not required, use the constructor that accepts name
	 * only.
	 * 
	 * @param id
	 * @param name
	 */
	public HierarchyLevel(String id, String name) {
		if (name == null || name.isEmpty()) {
			throw new NullPointerException("name can't be null or empty!");
		}
		this.id = id;
		this.name = name;
	}

	final String id;

	final String name;

}
