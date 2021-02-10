package de.cxp.ocs.client;

import java.util.List;

import de.cxp.ocs.model.suggest.Suggestion;
import feign.Param;
import feign.RequestLine;

interface SuggestApi {

	@RequestLine("GET /suggest-api/v1/{indexname}/suggest?userQuery={userQuery}&limit={limit}&filter={filter}")
	List<Suggestion> suggest(
			@Param("indexname") String indexname,
			@Param("userQuery") String userQuery,
			@Param("limit") int limit,
			@Param("filter") String filter) throws Exception;
}
