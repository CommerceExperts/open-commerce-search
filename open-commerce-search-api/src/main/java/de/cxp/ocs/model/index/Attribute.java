package de.cxp.ocs.model.index;

import lombok.Data;

/**
 * Attribute with ID, which can be used for filtering without requiring
 * a filtering based on Strings. The labels are used to produce nice facets.
 */
@Data
public class Attribute {

	public Attribute(String label) {
		if (label == null || label.isEmpty()) {
			throw new NullPointerException("label can't be null or empty!");
		}
		this.id = null;
		this.label = label;
	}

	/**
	 * if using this constructor, the ID is validated to be not null and not
	 * empty! In case ID is not required, use the constructor that accepts name
	 * only.
	 * 
	 * @param id
	 * @param label
	 */
	public Attribute(String id, String label) {
		if (label == null || label.isEmpty()) {
			throw new NullPointerException("name can't be null or empty!");
		}
		this.id = id;
		this.label = label;
	}

	final String id;

	final String label;

}
