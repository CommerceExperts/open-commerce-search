package de.cxp.ocs.indexer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.cxp.ocs.config.Field;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VariantItem extends DataItem {

	@JsonIgnore
	public VariantItem(MasterItem master) {
		this.master = master;
	}
	
	/**
	 * The master item that belongs to this variant.
	 */
	@JsonIgnore
	private MasterItem master;

	@JsonIgnore
	public MasterItem getMaster() {
		return master;
	}

	@Override
	public void setValue(Field field, Object value) {
		if (field.isVariantLevel()) {
			super.setValue(field, value);
		}
	}
}
