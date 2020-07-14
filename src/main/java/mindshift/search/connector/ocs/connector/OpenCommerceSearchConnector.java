package mindshift.search.connector.ocs.connector;

import mindshift.search.connector.api.v2.ConnectorConfigSkeleton;
import mindshift.search.connector.api.v2.ConnectorException;
import mindshift.search.connector.api.v2.ConnectorSkeleton;
import mindshift.search.connector.api.v2.models.CategorySearchRequest;
import mindshift.search.connector.api.v2.models.SearchRequest;
import mindshift.search.connector.api.v2.models.SearchResult;
import mindshift.search.connector.api.v2.models.SuggestRequest;
import mindshift.search.connector.api.v2.models.SuggestResult;
import mindshift.search.connector.ocs.service.OcsSearchService;
import mindshift.search.connector.ocs.service.OcsSuggestService;

/**
 * Open Commerce Search implementation of the SearchConnector.
 */
public class OpenCommerceSearchConnector extends ConnectorSkeleton<OpenCommerceSearchConnector> {

    private final OcsSearchService searchService;

    private final OcsSuggestService suggestService;

    /**
     * Constructor for the OCS connector.
     * 
     * @param metadata meta data
     * @param config configuration
     */
    public OpenCommerceSearchConnector(final Metadata metadata,
            final ConnectorConfigSkeleton<OpenCommerceSearchConnector> config) {
        super(metadata, config);
        searchService = new OcsSearchService();
        suggestService = new OcsSuggestService();
    }

    @Override
    public SearchResult category(final CategorySearchRequest categoryRequest)
            throws ConnectorException {
        return searchService.searchWithoutQuery(categoryRequest);
    }

    @Override
    public SearchResult search(final SearchRequest searchRequest) throws ConnectorException {
        return searchService.search(searchRequest);
    }

    @Override
    public SuggestResult suggest(final SuggestRequest suggestRequest) throws ConnectorException {
        return suggestService.suggest(suggestRequest);
    }

    @Override
    public void close() throws ConnectorException {
    }

}
