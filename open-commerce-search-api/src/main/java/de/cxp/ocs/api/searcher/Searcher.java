package de.cxp.ocs.api.searcher;

import java.io.IOException;

import de.cxp.ocs.model.params.SearchParams;
import de.cxp.ocs.model.params.SearchParams2;
import de.cxp.ocs.model.query.Query;
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
	 * @param parameters
	 * @return
	 * @throws IOException
	 */
	public SearchResult find(String tenant, Query query, SearchParams parameters) throws IOException;

	public SearchResult find(String tenant, SearchParams2 parameters) throws IOException;

}
