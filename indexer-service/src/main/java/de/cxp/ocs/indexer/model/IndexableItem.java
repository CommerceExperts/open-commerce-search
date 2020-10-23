package de.cxp.ocs.indexer.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * {@link DataItem} that can be used to be indexed directly. This is not
 * the case for sub-items such as {@link VariantItem}s
 */
@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class IndexableItem extends DataItem {

	private final String id;

	/**
	 * A list of categories a item belongs to.
	 */
	private final Map<String, Set<String>> categories = new HashMap<>();

}
