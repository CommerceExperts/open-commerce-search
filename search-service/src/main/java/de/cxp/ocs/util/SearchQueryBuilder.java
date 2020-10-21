package de.cxp.ocs.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;

import com.google.common.collect.Sets;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
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

		linkBuilder.addParameter("q", params.userQuery);
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

	private static String getSortingsString(List<Sorting> sortings) {
		StringBuilder sortingString = new StringBuilder();
		for (Sorting sorting : sortings) {
			if (sortingString.length() > 0) sortingString.append(VALUE_DELIMITER);
			if (SortOrder.DESC.equals(sorting.sortOrder)) sortingString.append(SORT_DESC_PREFIX);
			sortingString.append(sorting.field);
		}
		return sortingString.toString();
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

	public String withoutFilterAsLink(FacetConfig facetConfig, String... filterValues) {
		String filterValue = joinParameterValues(filterValues);
		if (isFilterSelected(facetConfig, filterValue)) {
			URIBuilder linkBuilder = new URIBuilder(searchQueryLink);
			if (facetConfig.isMultiSelect()) {
				Optional<Set<String>> existingFilterValues = linkBuilder.getQueryParams().stream()
						.filter(param -> facetConfig.getSourceField().equals(param.getName()))
						.findFirst()
						.map(NameValuePair::getValue)
						.map(value -> StringUtils.split(value, VALUE_DELIMITER))
						.map(Sets::newHashSet);
				
				if (existingFilterValues.isPresent() && existingFilterValues.get().size() > 1) {
					Set<String> values = existingFilterValues.get();
					values.remove(filterValue);
					linkBuilder.setParameter(facetConfig.getSourceField(), StringUtils.join(values, VALUE_DELIMITER));
				} else {
					List<NameValuePair> queryParams = linkBuilder.getQueryParams();
					queryParams.removeIf(nvp -> nvp.getName().equals(facetConfig.getSourceField()));
					linkBuilder.setParameters(queryParams);
				}
			}
			else {
				List<NameValuePair> queryParams = linkBuilder.getQueryParams();
				queryParams.removeIf(nvp -> nvp.getName().equals(facetConfig.getSourceField()));
				linkBuilder.setParameters(queryParams);
			}
			try {
				return linkBuilder.build().getRawQuery();
			}
			catch (URISyntaxException e) {
				throw new IllegalArgumentException("parameter caused URISyntaxException: " + facetConfig.getSourceField() + "=" + filterValue, e);
			}
		}
		else {
			return searchQueryLink.getRawQuery();

		}
	}

	public String withFilterAsLink(FacetConfig facetConfig, String... filterValues) {
		String filterValue = joinParameterValues(filterValues);
		if (isFilterSelected(facetConfig, filterValue)) {
			return searchQueryLink.toString();
		}
		if (searchQueryLink.toString().matches(".*[?&]" + Pattern.quote(facetConfig.getSourceField()) + "=.*")) {
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
				return linkBuilder.build().getRawQuery();
			}
			catch (URISyntaxException e) {
				throw new IllegalArgumentException("parameter caused URISyntaxException: " + facetConfig.getSourceField() + "=" + filterValue, e);
			}
		}
		else {
			String newParam = facetConfig.getSourceField() + "=" + encodeValue(filterValue);
			String query = searchQueryLink.getRawQuery();
			if (query.length() == 0) return newParam;
			else return query + "&" + newParam;
		}
	}

	private String encodeValue(String value) {
		try {
			return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
		}
		catch (Exception e) {
			return value;
		}
	}

	public boolean isFilterSelected(FacetConfig facetConfig, String filterValue) {
		return searchQueryLink.getQuery().matches("(^|.*?&)" + Pattern.quote(facetConfig.getSourceField()) + "=[^&]*?" + Pattern.quote(filterValue) + "($|[&,]).*");
	}

	public String toString() {
		return searchQueryLink.getRawQuery();
	}
}
