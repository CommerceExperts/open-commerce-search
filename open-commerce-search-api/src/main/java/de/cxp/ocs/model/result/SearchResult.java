package de.cxp.ocs.model.result;

import java.util.List;

import de.cxp.ocs.model.params.SearchParams;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SearchResult {

	/**
	 * amount of time the internal search needed to compute that result
	 */
	public long tookInMillis;

	/**
	 * the query that was used to perform that search.
	 */
	public String searchQuery;

	/**
	 * the params that were used to get that result view.
	 * May be used to generate breadcrumbs.
	 */
	public SearchParams params;


	public List<SearchResultSlice> slices;
}
