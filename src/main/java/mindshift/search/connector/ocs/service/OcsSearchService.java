package mindshift.search.connector.ocs.service;

import mindshift.search.connector.api.v2.models.CategorySearchRequest;
import mindshift.search.connector.api.v2.models.ResultItem;
import mindshift.search.connector.api.v2.models.SearchRequest;
import mindshift.search.connector.api.v2.models.SearchResult;

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
    public SearchResult search(final SearchRequest request) {
        // TODO fetch result from OCS

        SearchResult searchResult = new SearchResult();
        searchResult.setId(request.getId());
        searchResult.setOffset(request.getOffset());
        searchResult.setQ(request.getQ());
        ResultItem resultItem = new ResultItem();
        //        resultItem.code(id);
        searchResult.addItemsItem(resultItem);
        return searchResult;
    }

    /**
     * Request results for a category.
     * 
     * @param categoryRequest request
     * @return
     */
    public SearchResult searchWithoutQuery(final CategorySearchRequest categoryRequest) {
        // TODO fetch result from OCS

        SearchResult searchResult = new SearchResult();
        searchResult.setId(categoryRequest.getId());
        searchResult.setOffset(categoryRequest.getOffset());
        return searchResult;
    }
}
