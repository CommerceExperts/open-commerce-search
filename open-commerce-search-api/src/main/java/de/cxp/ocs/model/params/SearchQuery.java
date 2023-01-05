package de.cxp.ocs.model.params;

import javax.validation.constraints.Min;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * <p>
 * SearchQuery model that contains all "explicit" parameters for a search
 * requests, so not the filters. The main reason for this is how query-expansion
 * works with spring boot.
 * </p>
 * <p>
 * For a full search query, the extended class FilteredSearchQuery can be used.
 * </p>
 */
@NoArgsConstructor
@Accessors(chain = true)
@Data
public class SearchQuery {

	/**
	 * the user query.
	 */
	public String q;

	/**
	 * <p>
	 * Full sorting parameter value. This is the name of the sorting and
	 * optionally a dash as prefix, thats means the sorting should be
	 * descending. Several sorting criterion can be defined by separating the
	 * values using comma.
	 * </p>
	 * examples:
	 * <ul>
	 * <li>sort=price (ascending by price)</li>
	 * <li>sort=-price (descendending by price)</li>
	 * <li>sort=price,-name (price asc and name descending)</li>
	 * </ul>
	 */
	public String sort;

	@Min(1)
	public int limit = 12;

	@Min(0)
	public int offset = 0;

	/**
	 * flag to specify if facets should be returned with the requested response.
	 * Should be set to false in case only the next batch of hits is requested
	 * (e.g. for endless scrolling).
	 */
	public boolean withFacets = true;

	public String asUri() {
		StringBuilder uri = new StringBuilder();
		uri.append("q=").append(q);
		if (sort != null) uri.append("&sort=").append(sort);
		uri.append("&limit=").append(limit);
		if (offset > 0) uri.append("&offset=").append(offset);
		uri.append("&withFacets=").append(withFacets);
		return uri.toString();
	}

}
