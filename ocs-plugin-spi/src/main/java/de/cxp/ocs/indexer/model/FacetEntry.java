package de.cxp.ocs.indexer.model;

import java.util.Collection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Accessors(chain = true)
public class FacetEntry<T> {

	@NonNull
	private String name;

	// optional attribute ID
	private String id;

	@NonNull
	private Object value;

	public FacetEntry(String name) {
		this.name = name;
	}

	public FacetEntry(String name, T value) {
		this.name = name;
		this.value = value;
	}

	public FacetEntry(String name, Collection<T> values) {
		this.name = name;
		this.value = values;
	}

}
