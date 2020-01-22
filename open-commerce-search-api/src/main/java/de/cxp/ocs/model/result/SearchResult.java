package de.cxp.ocs.model.result;

import java.util.List;
import java.util.Map;

import de.cxp.ocs.model.params.SearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class SearchResult {

	/**
	 * amount of time the internal search needed to compute that result
	 */
	@Schema(description = "amount of time the internal search needed to compute that result")
	public long tookInMillis;

	/**
	 * The search parameters that were used to get that result view.
	 * May be used to generate breadcrumbs.
	 */
	@Schema(description = "The search parameters that were used to get that result view. May be used to generate breadcrumbs.")
	public SearchQuery inputQuery;

	/**
	 * The result may consist of several slices, for example if a search request
	 * couldn't be answered matching all words (e.g. "striped nike shirt") then
	 * one slice could be the result for one part of the query
	 * (e.g. "striped shirt") and the other could be for another part of the
	 * query (e.g. "nike shirt").
	 * 
	 * This can also be used to deliver some special advertised products or to
	 * split the result in different ranked slices (e.g. the first 3 results are
	 * ranked by popularity, the next 3 are sorted by price and the rest is
	 * ranked by 'default' relevance).
	 * 
	 * Each slice contains the {@link SearchQuery} that represent that exact
	 * slice.
	 * 
	 * At least 1 slice should be expected. If there is no slice, no results
	 * were found.
	 */
	@Schema(
			description = "The result may consist of several slices, for example if a search request couldn't be answered matching all words (e.g. \"striped nike shirt\")"
					+ " then one slice could be the result for one part of the query (e.g. \"striped shirt\")"
					+ " and the other could be for another part of the query (e.g. \"nike shirt\")."
					+ " This can also be used to deliver some special advertised products or to split the result in different ranked slices"
					+ " (e.g. the first 3 results are ranked by popularity, the next 3 are sorted by price and the rest is ranked by 'default' relevance)."
					+ " Each slice contains the {@link SearchQuery} that represent that exact slice. At least 1 slice should be expected. If there is no slice, no results were found.")
	public List<SearchResultSlice> slices;

	public List<Sorting> sortOptions;

	/**
	 * Additional optional payload, e.g. spell-correction information (aka
	 * did-you-mean)
	 */
	public Map<String, Object> meta;
}
