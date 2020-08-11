package mindshift.search.connector.ocs.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cxp.ocs.client.ApiClient;
import de.cxp.ocs.client.ApiException;
import de.cxp.ocs.client.api.SearchApi;
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

	private final ApiClient	ocsClient;
	private final SearchApi	searchService;

	public OcsSearchServiceAdapter(OcsConnectorConfig config) {
		ocsClient = new ApiClient();
		ocsClient.setBasePath(config.getSearchEndpoint());
		ocsClient.setUsername(config.getAuthUser());
		ocsClient.setPassword(config.getAuthPassword());

		searchService = new SearchApi(ocsClient);
	}

	/**
	 * Performs search for a given SearchState.
	 * 
	 * @param request
	 *        SearchResult
	 * @return SearchResult
	 * @throws ConnectorException
	 */
	public SearchResult search(final SearchRequest request) throws ConnectorException {
		String indexName = request.getAssortment() + "-" + request.getLocale();

		SearchQueryMapper queryMapper = new SearchQueryMapper(request);

		try {
			de.cxp.ocs.client.models.SearchResult ocsResult = searchService.search(indexName, queryMapper.getOcsQuery(), queryMapper.getOcsFilters());

			SearchResultMapper resultMapper = new SearchResultMapper(ocsResult, request);
			return resultMapper.toMindshiftResult();
		}
		catch (ApiException e) {
			String pattern = "Failure while processing requesting OCS Search Service! Root cause is `%s`.";
			String message = Messages.format(pattern, com.google.common.base.Throwables.getRootCause(e)).get();

			throw new ConnectorException(message, e);
		}

	}

	/**
	 * Request results for a category.
	 * 
	 * @param categoryRequest
	 *        request
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
