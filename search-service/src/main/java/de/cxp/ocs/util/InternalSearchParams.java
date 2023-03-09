package de.cxp.ocs.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.model.params.StaticProductSet;
import de.cxp.ocs.model.result.Sorting;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@NoArgsConstructor
@Accessors(chain = true)
@Data
public class InternalSearchParams {

	public int limit = 12;

	public int offset = 0;

	public String userQuery;

	/**
	 * flag to specify if facets are necessary. Should be set to false in case
	 * only the next batch of hits is requests (e.g. for endless scrolling).
	 */
	public boolean withFacets = true;

	/**
	 * Flag to specify if the full documents should be returned or not. Default:
	 * true.
	 * If set to "false", the documents are just returned with their IDs.
	 */
	public boolean withResultData = true;

	/**
	 * See ArrangedSearchQuery::includeMainResult
	 */
	public boolean includeMainResult = true;

	public List<Sorting> sortings = new ArrayList<>();

	public List<InternalResultFilter> filters = new ArrayList<>();

	/**
	 * Optional filters added by the analyzer that should only be applied internally but not exposed in the result.
	 */
	public List<InternalResultFilter> inducedFilters = new ArrayList<>();

	public Map<String, String> customParams;

	public StaticProductSet[] heroProductSets;

	public Set<String> excludedIds;

	public InternalSearchParams withSorting(Sorting sorting) {
		sortings.add(sorting);
		return this;
	}

	public InternalSearchParams withFilter(InternalResultFilter filter) {
		filters.add(filter);
		return this;
	}
}
