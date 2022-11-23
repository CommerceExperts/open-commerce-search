package de.cxp.ocs.config;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Represents a field configuration that is reversely fetched from the
 * search-index rather than from the configuration. With that it also contains
 * more information it may gather from the index.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IndexedField extends Field {

	public IndexedField(String name) {
		super(name);
	}

	private int valueCardinality = 0;

}
