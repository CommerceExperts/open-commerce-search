package de.cxp.ocs.util;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;

import com.google.common.util.concurrent.UncheckedExecutionException;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.SortOrder;
import de.cxp.ocs.model.result.Sorting;

public class SearchQueryBuilder {

	public static String	VALUE_DELIMITER			= ",";
	public static String	VALUE_DELIMITER_ENCODED	= "%2C";
	public static String	SORT_DESC_PREFIX		= "-";

	private final InternalSearchParams	searchParams;
	private final URI					searchQueryLink;

	public SearchQueryBuilder(InternalSearchParams params) {
		this.searchParams = params;
		searchQueryLink = toLink(params);
	}

	public static URI toLink(InternalSearchParams params) {
		InternalSearchParams defaultParams = new InternalSearchParams();
		URIBuilder linkBuilder = new URIBuilder();

		linkBuilder.addParameter("userQuery", params.userQuery);
		if (!params.filters.isEmpty()) {
			for (InternalResultFilter filter : params.filters) {
				linkBuilder.addParameter(filter.getField(), joinParameterValues(filter.getValues()));
			}
		}
		if (!params.sortings.isEmpty()) {
			linkBuilder.addParameter("sort", getSortingsString(params.sortings));
		}
		if (defaultParams.limit != params.limit) linkBuilder.addParameter("limit", String.valueOf(params.limit));
		if (defaultParams.offset != params.offset) linkBuilder.addParameter("offset", String.valueOf(params.offset));
		if (defaultParams.withFacets != params.withFacets) linkBuilder.addParameter("withFacets", String.valueOf(params.withFacets));

		try {
			return linkBuilder.build();
		}
		catch (URISyntaxException e) {
			throw new IllegalStateException("Unexpected Error. Most likely invalid Parameters caused it", e);
		}
	}

	public SearchQuery buildSearchQuery() {
		SearchQuery query = new SearchQuery();
		query.limit = searchParams.limit;
		query.offset = searchParams.offset;
		query.withFacets = searchParams.withFacets;
		query.userQuery = searchParams.userQuery;
		query.sort = searchParams.sortings.isEmpty() ? null : getSortingsString(searchParams.sortings);
		query.filters = getFiltersHashMap();
		return null;
	}

	private static String getSortingsString(List<Sorting> sortings) {
		StringBuilder sortingString = new StringBuilder();
		for (Sorting sorting : sortings) {
			if (sortingString.length() > 0) sortingString.append(VALUE_DELIMITER);
			if (SortOrder.DESC.equals(sorting.sortOrder)) sortingString.append(SORT_DESC_PREFIX);
			sortingString.append(sorting.field);
		}
		return sortingString.toString();
	}

	private Map<String, String> getFiltersHashMap() {
		if (searchParams.filters.isEmpty()) return null;
		Map<String, String> filterParams = new HashMap<>(searchParams.filters.size());
		for (InternalResultFilter rf : searchParams.filters) {
			filterParams.put(rf.getField(), joinParameterValues(rf.getValues()));
		}
		return filterParams;
	}

	private static String joinParameterValues(String... values) {
		if (values.length == 1) return StringUtils.replace(values[0], VALUE_DELIMITER, VALUE_DELIMITER_ENCODED);

		StringBuilder valuesString = new StringBuilder();
		if (values != null) {
			boolean appended = false;
			for (String v : values) {
				if (v != null) {
					if (appended) valuesString.append(',');
					valuesString.append(StringUtils.replace(v, VALUE_DELIMITER, VALUE_DELIMITER_ENCODED));
					appended = true;
				}
			}
		}
		return valuesString.toString();
	}

	// TODO test that shit
	public String withFilterAsLink(FacetConfig facetConfig, String filterValue) {
		if (searchQueryLink.toString().matches("[?&]" + facetConfig.getSourceField() + "=")) {
			URIBuilder linkBuilder = new URIBuilder(searchQueryLink);
			if (facetConfig.isMultiSelect()) {
				Optional<String> otherValues = linkBuilder.getQueryParams().stream()
						.filter(param -> facetConfig.getSourceField().equals(param.getName()))
						.findFirst()
						.map(NameValuePair::getValue);
				linkBuilder.setParameter(
						facetConfig.getSourceField(),
						otherValues
								.map(val -> val + VALUE_DELIMITER + joinParameterValues(filterValue))
								.orElse(filterValue));
			}
			else {
				linkBuilder.setParameter(facetConfig.getSourceField(), filterValue);
			}
			try {
				return linkBuilder.build().getQuery().toString();
			}
			catch (URISyntaxException e) {
				throw new IllegalArgumentException("parameter caused URISyntaxException: " + facetConfig.getSourceField() + "=" + filterValue, e);
			}
		}
		else {
			try {
				String newParam = facetConfig.getSourceField() + "=" + URLEncoder.encode(filterValue, StandardCharsets.UTF_8.name());
				String query = searchQueryLink.getQuery();
				if (query.length() == 0) return newParam;
				else return searchQueryLink.toString() + "&" + newParam;
			}
			catch (UnsupportedEncodingException e) {
				throw new UncheckedExecutionException(e);
			}

		}
	}
}
