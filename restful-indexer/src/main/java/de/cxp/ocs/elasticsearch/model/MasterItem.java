package de.cxp.ocs.elasticsearch.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class MasterItem extends IndexableItem {

	private String id;

	/**
	 * A list of categories a master item belongs to.
	 */
	private final Set<String> categories = new HashSet<>();

	/**
	 * A list of variants that belong to this master item. Can be empty
	 * in cases no variants are managed.
	 */
	private final List<VariantItem> variants = new ArrayList<>();
}
