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
	 * this is the name of the according data field. If a different label should
	 * be used, put that into meta data.
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
					+ "See the according FacetEntry variants for more details.",
			allowableValues = { "term", "hierarchical", "interval", "range" })
	public String type = "term";
	
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
	 *        key of the facet entry to add
	 * @param docCount
	 *        related document count of the facet entry to add
	 * @param link
	 *        related link of the facet entry to add
	 * @return
	 *         the changed facet
	 */
	public Facet addEntry(String key, long docCount, String link) {
		entries.add(new FacetEntry(key, null, docCount, link, false));
		return this;
	}

	/**
	 * Add facet entry to facet.
	 * 
	 * @param entry
	 *        the facet entry to add
	 * @return
	 *         the changed facet
	 */
	public Facet addEntry(FacetEntry entry) {
		entries.add(entry);
		return this;
	}
}
