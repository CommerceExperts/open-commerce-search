package mindshift.search.connector.ocs.service;

import mindshift.search.connector.models.SearchResult;
import mindshift.search.connector.models.SearchState;

/**
 * Open Commerce Search - Search Services.
 */
public class OcsSearchService {

    /**
     * Performs search for a given SearchState.
     * 
     * @param request SearchResult
     * @return SearchResult
     */
    public SearchResult search(final SearchState request) {
        SearchResult searchResult = new SearchResult();
        searchResult.setId(request.getId());
        searchResult.setOffset(request.getOffset());
        searchResult.setQ(request.getQ());
        return searchResult;
    }
}
