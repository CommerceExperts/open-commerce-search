package de.cxp.ocs.config;

import lombok.Data;

@Data
public class InternalSearchConfiguration {

	private final FieldConfigIndex fieldConfigIndex;

	public final SearchConfiguration provided;

	public InternalSearchConfiguration(FieldConfigIndex fieldConfigIndex, SearchConfiguration searchConfig) {
		this.fieldConfigIndex = fieldConfigIndex;
		provided = searchConfig;
	}

}
