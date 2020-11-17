package mindshift.search.connector.ocs.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cxp.ocs.client.SearchClient;
import de.cxp.ocs.client.deserializer.ObjectMapperFactory;
import feign.auth.BasicAuthRequestInterceptor;
import mindshift.search.commons.logging.Messages;
import mindshift.search.connector.api.v2.ConnectorException;
import mindshift.search.connector.api.v2.models.CategorySearchRequest;
import mindshift.search.connector.api.v2.models.SearchRequest;
import mindshift.search.connector.api.v2.models.SearchResult;
import mindshift.search.connector.ocs.connector.OcsConnectorConfig;
import mindshift.search.connector.ocs.mapper.SearchQueryMapper;
import mindshift.search.connector.ocs.mapper.SearchResultMapper;

/**
 * Open Commerce Search - Search Services.
 */
public class OcsSearchServiceAdapter {

    final Logger log = LoggerFactory.getLogger(OcsSearchServiceAdapter.class);

    private final SearchClient searchService;

    /**
     * Constructor of the OCS Adapter.
     * 
     * @param config
     */
    public OcsSearchServiceAdapter(final OcsConnectorConfig config) {
        if (config.getAuthUser() != null && config.getAuthPassword() != null) {
            searchService = new SearchClient(config.getSearchEndpoint(), clientBuilder -> {
                clientBuilder.decoder(ObjectMapperFactory.createJacksonDecoder());
                clientBuilder.requestInterceptor(new BasicAuthRequestInterceptor(
                        config.getAuthUser(), config.getAuthPassword()));
            });
        } else {
            searchService = new SearchClient(config.getSearchEndpoint());
        }
    }

    /**
     * Performs search for a given SearchState.
     * 
     * @param request SearchResult
     * @return SearchResult
     * @throws ConnectorException
     */
    public SearchResult search(final SearchRequest request) throws ConnectorException {
        String indexName = request.getAssortment() + "-" + request.getLocale();

        SearchQueryMapper queryMapper = new SearchQueryMapper(request);

        try {
            de.cxp.ocs.model.result.SearchResult ocsResult = searchService.search(indexName,
                    queryMapper.getOcsQuery(), queryMapper.getOcsFilters());

            SearchResultMapper resultMapper = new SearchResultMapper(ocsResult, request);
            return resultMapper.toMindshiftResult();
        } catch (Exception e) {
            String pattern = "Failure while processing requesting OCS Search Service! Root cause is `%s`.";
            String message = Messages
                    .format(pattern, com.google.common.base.Throwables.getRootCause(e)).get();

            throw new ConnectorException(message, e);
        }

    }

    /**
     * Request results for a category.
     * 
     * @param categoryRequest request
     * @return
     */
    public SearchResult searchWithoutQuery(final CategorySearchRequest categoryRequest) {
        // TODO fetch result from OCS without query not possible yet

        SearchResult searchResult = new SearchResult();
        searchResult.setId(categoryRequest.getId());
        searchResult.setOffset(categoryRequest.getOffset());
        return searchResult;
    }
}
