package de.cxp.ocs.indexer.model;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * {@link DataItem} that can be used to be indexed directly. This is not
 * the case for sub-items such as {@link VariantItem}s
 */
@AllArgsConstructor
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class IndexableItem extends DataItem {

	private final String id;

	/**
	 * A list of categories a item belongs to.
	 */
	private final List<FacetEntry<String>> pathFacetData = new ArrayList<>();

}
