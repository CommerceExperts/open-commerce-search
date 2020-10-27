package de.cxp.ocs.model.params;

import javax.validation.constraints.Min;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@NoArgsConstructor
@Accessors(chain = true)
@Data
public class SearchQuery {

	/**
	 * the user query.
	 */
	public String q;

	public SearchQuery setUserQuery(String userQuery) {
		q = userQuery;
		return this;
	}

	/**
	 * example:
	 * sort=price
	 * sort=-price (=> descendending)
	 * sort=price,-name (=> price asc and name descending)
	 */
	public String sort;

	@Min(1)
	public int limit = 12;

	@Min(0)
	public int offset = 0;

	/**
	 * flag to specify if facets are necessary. Should be set to false in case
	 * only the next batch of hits is requests (e.g. for endless scrolling).
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
