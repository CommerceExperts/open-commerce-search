package de.cxp.ocs.model.result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
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
	@Schema(description = "This is the name coming from the data. Separate label information should be available in the meta data.")
	@NonNull
	public String fieldName;

	/**
	 * This is the amount of matched documents that are covered by that facet.
	 */
	@Schema(description = "This is the amount of matched documents that are covered by that facet.")
	public long absoluteFacetCoverage = 0;

	/**
	 * Is set to true if there an active filter from that facet.
	 */
	@Schema(description = "Is set to true if there an active filter from that facet.")
	public boolean isFiltered = false;

	/**
	 * The entries of that facet.
	 */
	@Schema(description = "The entries of that facet.")
	public List<FacetEntry> entries = new ArrayList<>();

	@Schema(
			description = "The type of the facet, so the kind of FacetEntries it contains. "
					+ "To build a dynamic range slider, the first and the last entry of a interval facet can be used.",
			allowableValues = { "text", "hierarchical", "interval" })
	public String type = "text";
	
	/**
	 * Optional meta data for that facet, e.g. display hints like a label or a
	 * facet-type.
	 */
	@Schema(description = "Optional meta data for that facet, e.g. display hints like a label or a facet-type.")
	public Map<String, Object> meta = new HashMap<>();

	/**
	 * Add simple {@link FacetEntry} to the facet. Only meaningful for
	 * TextFacets.
	 * 
	 * @param key
	 * @param docCount
	 * @param link
	 * @return
	 */
	public Facet addEntry(String key, long docCount, String link) {
		entries.add(new FacetEntry(key, docCount, link));
		return this;
	}

	public Facet addEntry(FacetEntry entry) {
		entries.add(entry);
		return this;
	}
}
