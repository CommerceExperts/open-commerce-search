package de.cxp.ocs.model.params;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@NoArgsConstructor
@Accessors(chain = true)
@Data
public class SearchParams {

	public int limit = 12;

	public int offset = 0;

	/**
	 * flag to specify if facets are necessary. Should be set to false in case
	 * only the next batch of hits is requests (e.g. for endless scrolling).
	 */
	public boolean calculateFacet = true;

	/**
	 * Filters to restrict the result.
	 */
	public final List<ResultFilter> filters = new ArrayList<>();

	public final List<Sorting> sortings = new ArrayList<>();

	public SearchParams withFilter(ResultFilter filter) {
		filters.add(filter);
		return this;
	}

	public SearchParams withSorting(Sorting sorting) {
		sortings.add(sorting);
		return this;
	}
}
