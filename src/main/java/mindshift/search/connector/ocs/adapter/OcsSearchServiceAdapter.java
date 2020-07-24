package mindshift.search.connector.ocs.adapter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Comparators;

import de.cxp.ocs.client.ApiClient;
import de.cxp.ocs.client.ApiException;
import de.cxp.ocs.client.StringUtil;
import de.cxp.ocs.client.api.SearchApi;
import de.cxp.ocs.client.models.ResultHit;
import de.cxp.ocs.client.models.SearchQuery;
import de.cxp.ocs.client.models.SearchResultSlice;
import de.cxp.ocs.client.models.Sorting;
import de.cxp.ocs.client.models.Sorting.SortOrderEnum;
import mindshift.search.commons.logging.Messages;
import mindshift.search.connector.api.v2.ConnectorException;
import mindshift.search.connector.api.v2.models.Breadcrumb;
import mindshift.search.connector.api.v2.models.CategorySearchRequest;
import mindshift.search.connector.api.v2.models.Facet;
import mindshift.search.connector.api.v2.models.Facet.SelectorEnum;
import mindshift.search.connector.api.v2.models.NumericFacet;
import mindshift.search.connector.api.v2.models.NumericFacetValue;
import mindshift.search.connector.api.v2.models.ResultItem;
import mindshift.search.connector.api.v2.models.SearchRequest;
import mindshift.search.connector.api.v2.models.SearchResult;
import mindshift.search.connector.api.v2.models.Sort;
import mindshift.search.connector.api.v2.models.TextFacet;
import mindshift.search.connector.api.v2.models.TextFacetValue;
import mindshift.search.connector.ocs.connector.OcsConnectorConfig;
import mindshift.search.connector.ocs.util.SearchRequestBuilder;

/**
 * Open Commerce Search - Search Services.
 */
public class OcsSearchServiceAdapter {

	private static final String ENGINE_NAME = "ocs";

	private static final int MAX_FETCH_SIZE = 1_000;

	private static final String ADAPTER_VERSION = "1.0"; //?

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

		SearchQuery ocsQuery = new SearchQuery();
		ocsQuery.setQ(request.getQ());
		ocsQuery.setLimit(request.getFetchsize());
		ocsQuery.setOffset(request.getOffset().intValue());
		ocsQuery.setSort(translateSortParam(request.getSort()));
		Map<String, String> filters = extractFilters(request);

		try {
			de.cxp.ocs.client.models.SearchResult ocsResult = searchService.search(indexName, ocsQuery, filters);
			
			SearchResult searchResult = new SearchResult();
			searchResult.setEngine(ENGINE_NAME);
			searchResult.setMaxfetchsize(MAX_FETCH_SIZE);
			searchResult.setVersion(ADAPTER_VERSION);
			
			searchResult.setId(request.getId());
			searchResult.setAssortment(request.getAssortment());
			searchResult.setLocale(request.getLocale());
			searchResult.setOffset(request.getOffset());
			searchResult.setSort(request.getSort());
			searchResult.setQ(request.getQ());
			
			SearchRequestBuilder requestBuilder = new SearchRequestBuilder(request);
			
			searchResult.setBreadcrumbs(extractBreadCrumbs(ocsResult));
			searchResult.setFacets(extractFacets(ocsResult));
			searchResult.setSorts(extractSorts(ocsResult, requestBuilder));
			searchResult.setxPayload(ocsResult.getMeta());
			
			List<SearchResultSlice> resultSlices = ocsResult.getSlices();
			searchResult.setItems(extractResultItems(resultSlices));
			searchResult.setNumFound(resultSlices.stream().collect(Collectors.summarizingLong(SearchResultSlice::getMatchCount)).getSum());
			
			return searchResult;
		}
		catch (ApiException e) {
			String pattern = "Failure while processing requesting OCS Search Service! Root cause is `%s`.";
			String message = Messages.format(pattern, com.google.common.base.Throwables.getRootCause(e)).get();

			throw new ConnectorException(message, e);
		}
		
	}

	private List<Sort> extractSorts(de.cxp.ocs.client.models.SearchResult ocsResult, SearchRequestBuilder requestBuilder) {
		List<Sorting> sortOptions = ocsResult.getSortOptions();
		List<Sort> sorts = new ArrayList<>(sortOptions.size());
		
		for(Sorting sortOption :sortOptions) {
			Sort s = new Sort();
			s.setName(sortOption.getField());
			
			if (sortOption.getSortOrder().equals(SortOrderEnum.DESC)) {
				s.setCode("-" + sortOption.getField());
				s.setState(requestBuilder.withSort(sortOption.getField()+"-desc"));
			} else {
				s.setCode(sortOption.getField());
				s.setState(requestBuilder.withSort(sortOption.getField()));
			}
			
			sorts.add(s);
		}
		
		return sorts;
	}

	private List<ResultItem> extractResultItems(List<SearchResultSlice> resultSlices) {
		List<ResultItem> resultItems = new ArrayList<>();
		for (SearchResultSlice slice : resultSlices) {
			
			for(ResultHit hit : slice.getHits()) {
				ResultItem resultItem = new ResultItem();
				resultItem.setType("product");
				resultItem.setCode(hit.getDocument().getId());
				resultItem.setName(hit.getDocument().getData().getOrDefault("title", "").toString());
				resultItem.setUrl(hit.getDocument().getData().getOrDefault("productUrl", "").toString());
				resultItem.setxPayload(hit.getDocument().getData());
				resultItem.getxPayload().put("matchedQueries", hit.getMatchedQueries());
				resultItem.getxPayload().put("index", hit.getIndex());

				resultItems.add(resultItem);
			}
		}
		return resultItems;
	}

	private List<Facet> extractFacets(de.cxp.ocs.client.models.SearchResult ocsResult) {
		List<Facet> facets = new ArrayList<>();
		
		List<de.cxp.ocs.client.models.Facet> ocsFacets = null; 
		for (SearchResultSlice slice : ocsResult.getSlices()) {
			if (slice.getFacets() != null && !slice.getFacets().isEmpty()) {
				ocsFacets= slice.getFacets();
				break;
			}
		}
		
		if (ocsFacets != null) {
			for(de.cxp.ocs.client.models.Facet ocsFacet : ocsFacets) {
				String facetType = ocsFacet.getMeta().getOrDefault("type", "string").toString();
				boolean multiSelect = (boolean) ocsFacet.getMeta().getOrDefault("multiSelect", false);
				
				Facet facet;
				switch (facetType) {
					case "number":
						NumericFacet numFacet = new NumericFacet();
						List<NumericFacetValue> numValues = extractNumValues(ocsFacet.getEntries());
						numFacet.setTopvalues(numValues.stream().collect(
								Comparators.greatest(5,
										Comparator.comparingLong(NumericFacetValue::getCount))));
						numFacet.setValues(numValues);
						facet = numFacet;
						// Range facet not yet supported by OCS
					case "category":
					case "string":
					default:
						TextFacet textFacet = new TextFacet();
						List<TextFacetValue> textFacetValues = extractTextValues(ocsFacet.getEntries());
						textFacet.setTopvalues(textFacetValues.stream().collect(
										Comparators.greatest(5,
												Comparator.comparingLong(TextFacetValue::getCount))));
						textFacet.setValues(textFacetValues);
						facet = textFacet;
				}
				
				facet.setCode(ocsFacet.getFieldName());
				facet.setName(ocsFacet.getMeta().getOrDefault("label", facet.getCode()).toString());
				facet.setSelector(multiSelect ? SelectorEnum.OR : SelectorEnum.REFINE);
				facet.setType(facetType);
				facet.setxPayload(ocsFacet.getMeta());
				
				facets.add(facet);
			}
		}
		
		return facets;
	}

	private List<NumericFacetValue> extractNumValues(Object entries) {
		// TODO Auto-generated method stub
		return null;
	}

	private List<TextFacetValue> extractTextValues(Object entries) {
		// TODO Auto-generated method stub
		return null;
	}

	private List<Breadcrumb> extractBreadCrumbs(de.cxp.ocs.client.models.SearchResult ocsResult) {
		// TODO Auto-generated method stub
		return null;
	}

	private Map<String, String> extractFilters(final SearchRequest request) {
		Map<String, String> filters = new HashMap<>();
		for (Entry<String, Object> reqFilterEntry : request.getFilters().entrySet()) {
			if (reqFilterEntry.getValue() instanceof String) {
				filters.put(reqFilterEntry.getKey(), (String) reqFilterEntry.getValue());
			}
			else if (reqFilterEntry.getValue() instanceof String[]) {
				filters.put(reqFilterEntry.getKey(), StringUtil.join((String[]) reqFilterEntry.getValue(), ","));
			}
			else {
				log.error("can't handle filter '{}' with value of type '{}'", reqFilterEntry.getKey(), reqFilterEntry.getValue().getClass());
			}
		}
		return filters;
	}

	/**
	 * translate connector-api sort param to ocs-api sort param
	 * @param sort
	 * @return
	 */
	private String translateSortParam(String sort) {
		int splitIndex = sort.lastIndexOf('-');
		if (splitIndex == -1) {
			// sort only contains field name, which is the same behavior in OCS
			return sort;
		}
		else {
			String order = sort.substring(splitIndex+1);
			return (order.equals("desc") ? "-" : "") + sort.substring(0, splitIndex);
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
		// TODO fetch result from OCS

		SearchResult searchResult = new SearchResult();
		searchResult.setId(categoryRequest.getId());
		searchResult.setOffset(categoryRequest.getOffset());
		return searchResult;
	}
}
