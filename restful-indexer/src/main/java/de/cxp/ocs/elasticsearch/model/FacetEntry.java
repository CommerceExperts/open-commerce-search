package de.cxp.ocs.elasticsearch.model;

import java.util.Collection;

import de.cxp.ocs.util.Util;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class FacetEntry<T> {

	private String name;

	private Object value;

	public FacetEntry(String name) {
		this.name = name;
	}

	public FacetEntry(String name, T value) {
		this.name = name;
		this.value = value;
	}

	public FacetEntry<T> withValue(T additionalValue) {
		value = Util.collectObjects(value, additionalValue);
		return this;
	}

	public FacetEntry<T> withValues(Collection<T> additionalValue) {
		value = Util.collectObjects(value, additionalValue);
		return this;
	}

}
