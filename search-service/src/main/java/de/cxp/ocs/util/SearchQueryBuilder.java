package de.cxp.ocs.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Sets;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.model.result.SortOrder;
import de.cxp.ocs.model.result.Sorting;

public class SearchQueryBuilder {

	public static String VALUE_DELIMITER = ",";
	public static String VALUE_DELIMITER_ENCODED = "%2C";
	public static String SORT_DESC_PREFIX = "-";

	private final static InternalSearchParams defaultParams = new InternalSearchParams();

	private final Map<String, InternalResultFilter>	filters;
	private final Map<String, String> urlParams;
	private final URI searchQueryLink;

	public SearchQueryBuilder(InternalSearchParams params) {
		filters = new HashMap<>(params.filters.size());
		for (InternalResultFilter filter : params.filters) {
			filters.put(filter.getField().getName(), filter);
		}

		urlParams = toUrlParams(params);

		URIBuilder linkBuilder = new URIBuilder();
		urlParams.forEach(linkBuilder::addParameter);
		try {
			searchQueryLink = linkBuilder.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Unexpected Error. Most likely some invalid parameter caused it", e);
		}
	}

	private static Map<String, String> toUrlParams(InternalSearchParams params) {
		Builder<String, String> urlParams = ImmutableMap.<String, String>builder();
		if (params.userQuery != null) {
			urlParams.put("q", params.userQuery);
		}
		if (!params.filters.isEmpty()) {
			for (InternalResultFilter filter : params.filters) {
				String paramName = filter.getField().getName();
				if (filter.isFilterOnId()) {
					paramName += SearchParamsParser.ID_FILTER_SUFFIX;
				}
				urlParams.put(paramName, joinParameterValues(filter.getValues()));
			}
		}
		if (!params.sortings.isEmpty()) {
			urlParams.put("sort", getSortingsString(params.sortings));
		}
		if (defaultParams.limit != params.limit)
			urlParams.put("limit", String.valueOf(params.limit));
		if (defaultParams.offset != params.offset)
			urlParams.put("offset", String.valueOf(params.offset));
		if (defaultParams.withFacets != params.withFacets)
			urlParams.put("withFacets", String.valueOf(params.withFacets));
		return urlParams.build();
	}

	public static URI toLink(InternalSearchParams params) {
		URIBuilder linkBuilder = new URIBuilder();
		toUrlParams(params).forEach((k, v) -> linkBuilder.addParameter(k, v));
		try {
			return linkBuilder.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Unexpected Error. Most likely invalid Parameters caused it", e);
		}
	}

	public String withSortingLink(Field sortField, SortOrder sortOrder) {
		String sortString = sortStringRepresentation(sortField.getName(), sortOrder);
		URIBuilder linkBuilder = new URIBuilder();
		urlParams.forEach((param, value) -> {
			if (!"sort".equals(param)) {
				linkBuilder.addParameter(param, value);
			}
		});
		linkBuilder.addParameter("sort", sortString);
		try {
			return linkBuilder.build().getRawQuery();
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("parameter caused URISyntaxException: sort=" + sortString, e);
		}
	}

	public boolean isSortingActive(Field sortField, SortOrder order) {
		return urlParams.containsKey("sort") 
				&& urlParams.get("sort").matches("(^|.*,)"+Pattern.quote(sortStringRepresentation(sortField.getName(), order))+"($|,.*)");
	}

	public static String sortStringRepresentation(String fieldName, SortOrder order) {
		return (order.equals(SortOrder.DESC) ? "-" + fieldName : fieldName);
	}
	
	private static String getSortingsString(List<Sorting> sortings) {
		StringBuilder sortingString = new StringBuilder();
		for (Sorting sorting : sortings) {
			if (sortingString.length() > 0)
				sortingString.append(VALUE_DELIMITER);
			if (SortOrder.DESC.equals(sorting.sortOrder))
				sortingString.append(SORT_DESC_PREFIX);
			sortingString.append(sorting.field);
		}
		return sortingString.toString();
	}

	private static String joinParameterValues(String... values) {
		if (values.length == 1)
			return escapeValueDelimiter(values[0]);

		StringBuilder valuesString = new StringBuilder();
		if (values != null) {
			boolean appended = false;
			for (String v : values) {
				if (v != null) {
					if (appended)
						valuesString.append(',');
					valuesString.append(escapeValueDelimiter(v));
					appended = true;
				}
			}
		}
		return valuesString.toString();
	}

	public String withoutFilterAsLink(FacetConfig facetConfig, String... filterValues) {
		String filterName = getFilterName(facetConfig);
		String removeValue = joinParameterValues(filterValues);
		if (isFilterSelected(filterName, removeValue)) {
			URIBuilder linkBuilder = new URIBuilder(searchQueryLink);
			if (facetConfig.isMultiSelect()) {
				Optional<Set<String>> existingFilterValues = linkBuilder.getQueryParams().stream()
						.filter(param -> filterName.equals(param.getName())).findFirst()
						.map(NameValuePair::getValue).map(value -> StringUtils.split(value, VALUE_DELIMITER))
						.map(Sets::newHashSet);

				if (existingFilterValues.isPresent() && existingFilterValues.get().size() > 1) {
					Set<String> values = existingFilterValues.get();
					values.remove(removeValue);
					linkBuilder.setParameter(filterName, StringUtils.join(values, VALUE_DELIMITER));
				} else {
					List<NameValuePair> queryParams = linkBuilder.getQueryParams();
					queryParams.removeIf(nvp -> nvp.getName().equals(filterName));
					linkBuilder.setParameters(queryParams);
				}
			} else {
				List<NameValuePair> queryParams = linkBuilder.getQueryParams();
				queryParams.removeIf(nvp -> nvp.getName().equals(filterName));
				linkBuilder.setParameters(queryParams);
			}
			try {
				return linkBuilder.build().getRawQuery();
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException(
						"parameter caused URISyntaxException: " + facetConfig.getSourceField() + "=" + removeValue, e);
			}
		} else {
			return searchQueryLink.getRawQuery();
		}
	}

	/**
	 * the complete filter parameter and returns the link.
	 * 
	 * @param facetConfig
	 * @return
	 */
	public String withoutFilterAsLink(FacetConfig facetConfig) {
		String filterName = getFilterName(facetConfig);
		if (containsParameter(filterName)) {
			URIBuilder linkBuilder = new URIBuilder(searchQueryLink);
			List<NameValuePair> queryParams = linkBuilder.getQueryParams();
			queryParams.removeIf(nvp -> nvp.getName().equals(filterName));
			linkBuilder.setParameters(queryParams);
			try {
				return linkBuilder.build().getRawQuery();
			}
			catch (URISyntaxException e) {
				// should be impossible
				throw new IllegalArgumentException("removing parameter " + filterName + " caused URISyntaxException", e);
			}
		}
		else {
			return searchQueryLink.getRawQuery();
		}
	}

	private String getFilterName(FacetConfig facetConfig) {
		String filterName = facetConfig.getSourceField();
		InternalResultFilter filter = filters.get(filterName);
		if (filter != null && filter.isFilterOnId()) {
			filterName += SearchParamsParser.ID_FILTER_SUFFIX;
		}
		return filterName;
	}

	public String withFilterAsLink(FacetConfig facetConfig, String... filterInputValues) {
		String filterName = getFilterName(facetConfig);
		String filterValues = joinParameterValues(filterInputValues);
		if (isFilterSelected(filterName, filterValues)) {
			return searchQueryLink.getRawQuery();
		}
		if (searchQueryLink.toString().matches(".*[?&]" + Pattern.quote(filterName) + "=.*")) {
			URIBuilder linkBuilder = new URIBuilder(searchQueryLink);
			if (facetConfig.isMultiSelect()) {
				Optional<String> otherValues = linkBuilder.getQueryParams().stream()
						.filter(param -> filterName.equals(param.getName())).findFirst()
						.map(NameValuePair::getValue);
				linkBuilder.setParameter(filterName, otherValues
						.map(val -> val + VALUE_DELIMITER + filterValues).orElse(filterValues));
			} else {
				linkBuilder.setParameter(filterName, filterValues);
			}
			try {
				return linkBuilder.build().getRawQuery();
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException(
						"parameter caused URISyntaxException: " + filterName + "=" + filterValues, e);
			}
		} else {
			return withParameterAppended(filterName, filterValues);
		}
	}

	private String withParameterAppended(String filterName, String filterValues) {
		String newParam = filterName + "=" + urlEncodeValue(filterValues);
		String query = searchQueryLink.getRawQuery();
		if (query == null || query.length() == 0) {
			return newParam;
		}
		else {
			return query + "&" + newParam;
		}
	}

	/**
	 * <p>
	 * Returns a URL with that filter set.
	 * If the filter-name existed before, it will be replaced completely.
	 * No multi-select value merging will be done.
	 * </p>
	 * <p>
	 * This method moves the responsibility of value-joining to the caller
	 * </p>
	 * 
	 * @param facetConfig
	 * @param filterInputValues
	 * @return
	 */
	public String withExactFilterAsLink(FacetConfig facetConfig, String... filterInputValues) {
		String filterName = getFilterName(facetConfig);
		String filterValues = joinParameterValues(filterInputValues);
		if (containsParameter(filterName)) {
			URIBuilder linkBuilder = new URIBuilder(searchQueryLink);
			linkBuilder.setParameter(filterName, filterValues);
			try {
				return linkBuilder.build().getRawQuery();
			}
			catch (URISyntaxException e) {
				throw new IllegalArgumentException(
						"parameter caused URISyntaxException: " + filterName + "=" + filterValues, e);
			}
		}
		else {
			return withParameterAppended(filterName, filterValues);
		}
	}

	private boolean containsParameter(String filterName) {
		return searchQueryLink.getQuery() != null && searchQueryLink.getRawQuery().contains(filterName + "=");
	}

	/**
	 * Checks if that parameter value is set for that parameter key.
	 * The name and value MUST NOT be url-encoded.
	 * However if the value contains a comma value that is not meant as a
	 * multi-value-delimiter, it has to be URL-encoded so that it will be double
	 * URL encoded.
	 * 
	 * @param paramName
	 * @param filterValue
	 * @return
	 */
	private boolean isFilterSelected(String paramName, String filterValue) {
		return searchQueryLink.getQuery() != null && searchQueryLink.getRawQuery().matches("(^|.*?&)"
				+ Pattern.quote(urlEncodeValue(paramName)) + "=[^&]*?"
				+ Pattern.quote(urlEncodeValue(filterValue)) + "($|&|%2C).*");
	}

	private String urlEncodeValue(String value) {
		try {
			return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
		} catch (Exception e) {
			return value;
		}
	}
	
	private static String escapeValueDelimiter(String value) {
		if (value.contains(VALUE_DELIMITER)) {
			return value.replace(VALUE_DELIMITER, VALUE_DELIMITER_ENCODED);
		} else {
			return value;
		}
	}

	public String toString() {
		return searchQueryLink.getRawQuery();
	}


}
