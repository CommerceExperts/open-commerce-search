package de.cxp.ocs.model.index;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;

/**
 * Attribute with ID, which can be used for filtering without requiring
 * a filtering based on Strings. The labels are used to produce nice facets.
 */
@Accessors(chain = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Attribute {
	
	@NonNull
	public String id;

	@NonNull
	public String label;
	
	public static Attribute of(String name) {
		return new Attribute(name, name);
	}

	public static Attribute of(String id, String name) {
		return new Attribute(id, name);
	}
}