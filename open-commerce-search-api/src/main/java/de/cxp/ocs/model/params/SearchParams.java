package de.cxp.ocs.model.params;

import java.util.ArrayList;
import java.util.List;

import de.cxp.ocs.util.Sorting;
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
	public boolean hasFacets = true;

	public final List<String> sort = new ArrayList<>();

	public SearchParams withSorting(Sorting sorting) {
		sort.add(sorting.getStringRepresentation());
		return this;
	}
}
