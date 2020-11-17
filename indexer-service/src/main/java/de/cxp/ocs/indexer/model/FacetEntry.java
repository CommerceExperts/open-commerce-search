package de.cxp.ocs.indexer.model;

import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.cxp.ocs.util.Util;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FacetEntry<T> {

	@NonNull
	private String name;

	// optional attribute ID
	private String id;

	@NonNull
	private Object value;

	// optional attribute value code
	private String code;

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
