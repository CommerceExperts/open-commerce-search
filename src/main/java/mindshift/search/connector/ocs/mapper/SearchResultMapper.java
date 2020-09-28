package mindshift.search.connector.ocs.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.common.collect.Comparators;

import de.cxp.ocs.client.models.FacetEntry;
import de.cxp.ocs.client.models.HierarchialFacetEntry;
import de.cxp.ocs.client.models.ResultHit;
import de.cxp.ocs.client.models.SearchResultSlice;
import de.cxp.ocs.client.models.Sorting;
import de.cxp.ocs.client.models.Sorting.SortOrderEnum;
import mindshift.search.connector.api.v2.models.Breadcrumb;
import mindshift.search.connector.api.v2.models.Facet;
import mindshift.search.connector.api.v2.models.Facet.SelectorEnum;
import mindshift.search.connector.api.v2.models.RangeFacet;
import mindshift.search.connector.api.v2.models.ResultItem;
import mindshift.search.connector.api.v2.models.SearchRequest;
import mindshift.search.connector.api.v2.models.SearchResult;
import mindshift.search.connector.api.v2.models.Sort;
import mindshift.search.connector.api.v2.models.TextFacet;
import mindshift.search.connector.api.v2.models.TextFacetValue;

/**
 * Maps the OCS SearchResult to the MindShift SearchResult object.
 */
public class SearchResultMapper {

	private static final String ENGINE_NAME = "ocs";

	private static final int MAX_FETCH_SIZE = 1_000;

	private static final String ADAPTER_VERSION = "1.0"; // ?

	final de.cxp.ocs.client.models.SearchResult ocsResult;

	final SearchRequest request;

	/**
	 * Constructor of the SearchResultMapper.
	 * 
	 * @param ocsResult
	 * @param request
	 */
	public SearchResultMapper(final de.cxp.ocs.client.models.SearchResult ocsResult, final SearchRequest request) {
		this.ocsResult = ocsResult;
		this.request = request;
	}

	/**
	 * Build and return mindshift compliant result.
	 * 
	 * @return
	 */
	public SearchResult toMindshiftResult() {
		final SearchResult searchResult = new SearchResult();
		searchResult.setEngine(ENGINE_NAME);
		searchResult.setMaxfetchsize(MAX_FETCH_SIZE);
		searchResult.setVersion(ADAPTER_VERSION);

		searchResult.setId(request.getId());
		searchResult.setAssortment(request.getAssortment());
		searchResult.setLocale(request.getLocale());
		searchResult.setOffset(request.getOffset());
		searchResult.setSort(request.getSort());
		searchResult.setQ(request.getQ());

		final MindshiftSearchRequestBuilder requestBuilder = new MindshiftSearchRequestBuilder(request);
		searchResult.setBreadcrumbs(extractBreadCrumbs(request));
		searchResult.setFacets(extractFacets(ocsResult, requestBuilder));
		searchResult.setSorts(extractSorts(ocsResult, requestBuilder));
		searchResult.setxPayload(ocsResult.getMeta());

		final List<SearchResultSlice> resultSlices = ocsResult.getSlices();
		searchResult.setItems(extractResultItems(resultSlices));
		searchResult.setNumFound(resultSlices.stream().collect(Collectors.summarizingLong(SearchResultSlice::getMatchCount)).getSum());

		return searchResult;
	}

	private List<Sort> extractSorts(final de.cxp.ocs.client.models.SearchResult ocsResult, final MindshiftSearchRequestBuilder requestBuilder) {
		final List<Sorting> sortOptions = ocsResult.getSortOptions();
		if (sortOptions == null) return Collections.emptyList();

		final List<Sort> sorts = new ArrayList<>(sortOptions.size());

		for (Sorting sortOption : sortOptions) {
			final Sort s = new Sort();
			s.setName(sortOption.getField());

			if (sortOption.getSortOrder().equals(SortOrderEnum.DESC)) {
				s.setCode("-" + sortOption.getField());
				s.setState(requestBuilder.withSort(sortOption.getField() + "-desc"));
			}
			else {
				s.setCode(sortOption.getField());
				s.setState(requestBuilder.withSort(sortOption.getField()));
			}

			sorts.add(s);
		}

		return sorts;
	}

	private List<ResultItem> extractResultItems(List<SearchResultSlice> resultSlices) {
		final List<ResultItem> resultItems = new ArrayList<>();
		for (final SearchResultSlice slice : resultSlices) {

			for (final ResultHit hit : slice.getHits()) {
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

	private List<Facet> extractFacets(final de.cxp.ocs.client.models.SearchResult ocsResult, final MindshiftSearchRequestBuilder requestBuilder) {
		final List<Facet> facets = new ArrayList<>();

		List<de.cxp.ocs.client.models.Facet> ocsFacets = null;
		for (SearchResultSlice slice : ocsResult.getSlices()) {
			if (slice.getFacets() != null && !slice.getFacets().isEmpty()) {
				// only take the facets from the first slice with facets, since
				// we only expect one slice with facets at all
				ocsFacets = slice.getFacets();
				break;
			}
		}

		if (ocsFacets != null) {
			for (de.cxp.ocs.client.models.Facet ocsFacet : ocsFacets) {
				final String facetType = ocsFacet.getMeta().getOrDefault("type", "string").toString();
				final boolean multiSelect = (boolean) ocsFacet.getMeta().getOrDefault("multiSelect", false);

				final Facet facet;
				switch (facetType) {
					case "number":
						final RangeFacet rangeFacet = new RangeFacet();
						// XXX: Range facet not yet supported by OCS
						facet = rangeFacet;
						break;
					// TODO there are some cases, where single numeric
					// values are the best suitable facet type
					// case "special-number-terms":
					case "category":
					case "string":
					default:
						final TextFacet textFacet = new TextFacet();
						final List<TextFacetValue> textFacetValues = extractTextValues(ocsFacet.getEntries(), ocsFacet.getFieldName(), requestBuilder);
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

	private List<TextFacetValue> extractTextValues(final List<FacetEntry> entries, String facetKey, final MindshiftSearchRequestBuilder requestBuilder) {
		final List<TextFacetValue> textFacet = new ArrayList<>(entries.size());
		for (final FacetEntry entry : entries) {
			TextFacetValue textFacetValue = new TextFacetValue();
			textFacetValue.setCode(entry.getKey());
			textFacetValue.setCount(entry.getDocCount());
			// XXX OCS has no support for labels, yet
			textFacetValue.setName(entry.getKey());
			textFacetValue.setSelected(requestBuilder.hasFilter(facetKey, entry.getKey()));
			textFacetValue.setState(requestBuilder.withFilter(facetKey, entry.getKey()));

			if (entry.getType().equals("hierarchical")) {
				final List<TextFacetValue> children = extractTextValues(((HierarchialFacetEntry) entry).getChildren(), facetKey, requestBuilder);
				textFacetValue.setChildren(children);
			}

			textFacet.add(textFacetValue);
		}
		return textFacet;
	}

	private List<Breadcrumb> extractBreadCrumbs(final SearchRequest request) {
		final List<Breadcrumb> breadCrumbs = new ArrayList<>();

		final SearchRequest state = new SearchRequest();
		state.setQ(request.getQ());

		Breadcrumb crumb = new Breadcrumb();
		crumb.setCode("query");
		crumb.setLabel(request.getQ());
		crumb.setState(MindshiftSearchRequestBuilder.cloneRequest(state));
		breadCrumbs.add(crumb);

		for (final Entry<String, Object> filterEntry : request.getFilters().entrySet()) {
			state.putFiltersItem(filterEntry.getKey(), filterEntry.getValue());

			crumb = new Breadcrumb();
			crumb.setCode(filterEntry.getKey());
			crumb.setLabel(filterEntry.getValue().toString());
			crumb.setState(MindshiftSearchRequestBuilder.cloneRequest(state));
			breadCrumbs.add(crumb);
		}

		return breadCrumbs;
	}
}
