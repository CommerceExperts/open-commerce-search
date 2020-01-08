package de.cxp.ocs.model.result;

import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SearchResultSlice {

	public String label;
	
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
	
	public boolean hasMore() {
		return nextOffset < matchCount;
	}
}
