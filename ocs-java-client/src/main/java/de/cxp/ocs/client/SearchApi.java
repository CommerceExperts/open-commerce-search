package de.cxp.ocs.client;

import java.util.Map;

import de.cxp.ocs.model.result.SearchResult;
import feign.Param;
import feign.QueryMap;
import feign.RequestLine;

interface SearchApi {

	@RequestLine("GET /search-api/v1/tenants")
	String[] getTenants();

	@RequestLine("GET /search-api/v1/search/{tenant}?q={q}&sort={sort}&offset={offset}&limit={limit}&withFacets={withFacets}")
	SearchResult search(
			@Param("tenant") String tenant,
			@Param("q") String q,
			@Param("sort") String sort,
			@Param("offset") int offset,
			@Param("limit") int limit,
			@Param("withFacets") boolean withFacets,
			@QueryMap Map<String, String> filters) throws Exception;
}
