package de.cxp.ocs.model.result;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SearchResultSlice {

	/**
	 * the absolute number of matches.
	 */
	public long matchCount;

	/**
	 * the offset value to use to get the next result batch
	 */
	public long nextOffset;
	
	/**
	 * the list of actual hits for that result view.
	 */
	public List<ResultHit> hits;

	public List<Facet> facets;
	
	private final Map<String, Object> meta = new HashMap<>();

	public boolean hasMore() {
		return nextOffset < matchCount;
	}
	
	/**
	 * Convenient method to add a label as meta data.
	 * 
	 * @param label
	 * @return
	 */
	public SearchResultSlice setLabel(String label) {
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
		return (String) meta.getOrDefault("label", "");
	}
}
