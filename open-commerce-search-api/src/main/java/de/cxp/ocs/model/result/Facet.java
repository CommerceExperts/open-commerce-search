package de.cxp.ocs.model.result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Data
public class Facet {

	/**
	 * this is the name coming from the data. If a separate label should be
	 * used, put that into meta data.
	 */
	@Setter
	@NonNull
	private String fieldName;

	private long absoluteFacetCoverage = 0;

	private byte order = Byte.MAX_VALUE;

	private boolean isFiltered = false;

	private List<FacetEntry> entries = new ArrayList<>();

	private final Map<String, Object> meta = new HashMap<>();

	public Facet addEntry(String key, long docCount) {
		entries.add(new FacetEntry(key, docCount));
		return this;
	}

	public Facet addEntry(FacetEntry entry) {
		entries.add(entry);
		return this;
	}

	/**
	 * Convenient method to add a label as meta data.
	 * 
	 * @param label
	 * @return
	 */
	public Facet setLabel(String label) {
		meta.put("label", label);
		return this;
	}

	/**
	 * Convenient method to retrieve a label as meta data. If none is set, it
	 * returns this facet's fieldName.
	 * 
	 * @return
	 */
	public String getLabel() {
		return (String) meta.getOrDefault("label", fieldName);
	}

	/**
	 * Convenient method to set a type for that fact. The type can be some kind
	 * of visualization hint (e.g. "numeric" for some kind of range
	 * visualization).
	 * 
	 * @param type
	 */
	public Facet setType(String type) {
		meta.put("type", type);
		return this;
	}

}
