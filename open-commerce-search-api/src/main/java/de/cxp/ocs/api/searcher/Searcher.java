package de.cxp.ocs.api.searcher;

import java.io.IOException;

import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.SearchResult;

public interface Searcher {

	/**
	 * Search the index using the given searchQuery.
	 * 
	 * Each tenant can have its own configuration. Different tenants may still
	 * use the same indexes. This is defined by the underlying configuration.
	 * 
	 * @param tenant
	 * @param searchQuery
	 * @param searchQueryParameters
	 * @return
	 * @throws IOException
	 */
	public SearchResult find(String tenant, SearchQuery searchQueryParameters) throws IOException;

}
