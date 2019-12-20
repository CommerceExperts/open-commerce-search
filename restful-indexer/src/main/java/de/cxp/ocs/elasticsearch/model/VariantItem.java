package de.cxp.ocs.elasticsearch.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@AllArgsConstructor(onConstructor_ = { @JsonIgnore })
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VariantItem extends IndexableItem {

	/**
	 * The master item that belongs to this variant.
	 */
	@JsonIgnore
	private MasterItem master;

	@JsonIgnore
	public MasterItem getMaster() {
		return master;
	}
}
