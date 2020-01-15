package de.cxp.ocs.model.result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Data
public class Facet {

	/**
	 * this is the name coming from the data. If a separate label should be
	 * used, put that into meta data.
	 */
	@NonNull
	public String fieldName;

	/**
	 * This is the amount of matched documents that are covered by that facet.
	 */
	public long absoluteFacetCoverage = 0;

	/**
	 * Is set to true if there an active filter from that facet.
	 */
	public boolean isFiltered = false;

	/**
	 * The entries of that facet.
	 */
	public List<FacetEntry> entries = new ArrayList<>();

	/**
	 * Optional meta data for that facet, e.g. display hints like a label or a
	 * facet-type.
	 */
	public Map<String, Object> meta = new HashMap<>();

	public Facet addEntry(String key, long docCount, String link) {
		entries.add(new FacetEntry(key, docCount, link));
		return this;
	}

	public Facet addEntry(FacetEntry entry) {
		entries.add(entry);
		return this;
	}
}
