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
	public OpenCommerceSearchConnector(final Metadata metadata, final OcsConnectorConfig config) {
        super(metadata, config);
        searchService = new OcsSearchServiceAdapter(config);
        suggestService = new OcsSuggestServiceAdapter();
    }

	/**
	 * Do category/navigation request.
	 * 
	 * @return SearchResult
	 */
    @Override
	public SearchResult category(final Subject subject, final CategorySearchRequest categoryRequest)
            throws ConnectorException {
        return searchService.searchWithoutQuery(categoryRequest);
    }

	/**
	 * Do OCS search.
	 * 
	 * @return SearchResult
	 */
    @Override
	public SearchResult search(final Subject subject, final SearchRequest searchRequest) throws ConnectorException {
        return searchService.search(searchRequest);
    }

	/**
	 * Do Suggest request.
	 * 
	 * @return SearchResult
	 */
    @Override
	public SuggestResult suggest(final Subject subject, final SuggestRequest suggestRequest) throws ConnectorException {
        return suggestService.suggest(suggestRequest);
    }

	/**
	 * Optionally close open resources and connections. Not here, however.
	 */
    @Override
    public void close() throws ConnectorException {
    }

}
