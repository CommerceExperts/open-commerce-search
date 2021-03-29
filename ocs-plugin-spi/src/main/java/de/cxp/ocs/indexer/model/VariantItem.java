package de.cxp.ocs.indexer.model;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VariantItem extends DataItem {

	public VariantItem(MasterItem master) {
		this.master = master;
	}
	
	/**
	 * The master item that belongs to this variant.
	 */
	private MasterItem master;

	public MasterItem getMaster() {
		return master;
	}

}
