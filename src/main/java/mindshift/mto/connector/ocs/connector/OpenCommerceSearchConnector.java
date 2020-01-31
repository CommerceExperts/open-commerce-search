package mindshift.mto.connector.ocs.connector;

import mindshift.mto.connector.api.v2.ConnectorException;
import mindshift.mto.connector.api.v2.SearchConnector;
import mindshift.mto.connector.ocs.service.OcsSearchService;
import mindshift.mto.connector.ocs.service.OcsSuggestService;
import mindshift.search.connector.models.SearchResult;
import mindshift.search.connector.models.SearchState;
import mindshift.search.connector.models.SuggestRequest;
import mindshift.search.connector.models.SuggestResult;

/**
 * Open Commerce Search implementation of the SearchConnector.
 */
public class OpenCommerceSearchConnector implements SearchConnector {

    private final OcsSearchService searchService;

    private final OcsSuggestService suggestService;

    /**
     * Creates OpenCommerceSearchConnector.
     */
    public OpenCommerceSearchConnector() {
        searchService = new OcsSearchService();
        suggestService = new OcsSuggestService();
    }

    @Override
    public SearchResult search(final SearchState searchState) throws ConnectorException {
        return searchService.search(searchState);
    }

    @Override
    public SuggestResult suggest(final SuggestRequest suggestRequest) throws ConnectorException {
        return suggestService.suggest(suggestRequest);
    }
}
