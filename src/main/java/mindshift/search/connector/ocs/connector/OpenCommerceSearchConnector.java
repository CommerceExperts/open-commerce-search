package mindshift.search.connector.ocs.connector;

import javax.security.auth.Subject;

import mindshift.search.connector.api.v2.ConnectorException;
import mindshift.search.connector.api.v2.ConnectorSkeleton;
import mindshift.search.connector.api.v2.models.CategorySearchRequest;
import mindshift.search.connector.api.v2.models.SearchRequest;
import mindshift.search.connector.api.v2.models.SearchResult;
import mindshift.search.connector.api.v2.models.SuggestRequest;
import mindshift.search.connector.api.v2.models.SuggestResult;
import mindshift.search.connector.ocs.adapter.OcsSearchServiceAdapter;
import mindshift.search.connector.ocs.adapter.OcsSuggestServiceAdapter;

/**
 * Open Commerce Search implementation of the SearchConnector.
 */
public class OpenCommerceSearchConnector extends ConnectorSkeleton<OpenCommerceSearchConnector> {

    private final OcsSearchServiceAdapter searchService;

    private final OcsSuggestServiceAdapter suggestService;

    /**
     * Constructor for the OCS connector.
     * 
     * @param metadata meta data
     * @param config configuration
     */
    public OpenCommerceSearchConnector(final Metadata metadata, OcsConnectorConfig config) {
        super(metadata, config);
        searchService = new OcsSearchServiceAdapter(config);
        suggestService = new OcsSuggestServiceAdapter();
    }

    @Override
	public SearchResult category(Subject subject, CategorySearchRequest categoryRequest)
            throws ConnectorException {
        return searchService.searchWithoutQuery(categoryRequest);
    }

    @Override
	public SearchResult search(Subject subject, SearchRequest searchRequest) throws ConnectorException {
        return searchService.search(searchRequest);
    }

    @Override
	public SuggestResult suggest(Subject subject, SuggestRequest suggestRequest) throws ConnectorException {
        return suggestService.suggest(suggestRequest);
    }

    @Override
    public void close() throws ConnectorException {
    }

}
