package de.cxp.ocs.indexer.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

@Data
@EqualsAndHashCode(callSuper = true)
public class MasterItem extends IndexableItem {

	public MasterItem(@NonNull String id) {
		super(id);
	}

	/**
	 * A list of variants that belong to this master item. Can be empty
	 * in cases no variants are managed.
	 */
	private final List<VariantItem> variants = new ArrayList<>();

}
