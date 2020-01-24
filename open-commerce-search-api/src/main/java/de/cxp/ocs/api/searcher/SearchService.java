package de.cxp.ocs.api.searcher;

import java.io.IOException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.SearchResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

@Server(url = "http://search-service")
@Tag(name = "search")
@Path("search-api/v1")
public interface SearchService {

	/**
	 * Search the index using the given searchQuery.
	 * 
	 * Each tenant can have its own configuration. Different tenants may still
	 * use the same indexes. This is defined by the underlying configuration.
	 * 
	 * @param tenant
	 * @param searchQuery
	 * @param parameters
	 * @return
	 * @throws IOException
	 */
	/**
	 * @param tenant
	 * @param searchQuery
	 * @return
	 * @throws IOException
	 */
	@GET
	@Path("{tenant}")
	@Operation(
			summary = "Search for documents",
			description = "Runs a search request for a certain tenant."
					+ " The tenant should exist at the service and linked to a certain index in the backend."
					+ " Different tenants may use the same index.",
			parameters = {
					@Parameter(
							in = ParameterIn.PATH,
							name = "tenant",
							description = "tenant name",
							required = true),
					@Parameter(
							in = ParameterIn.QUERY,
							name = "searchQuery",
							explode = Explode.TRUE,
							style = ParameterStyle.FORM,
							description = "the query that describes the wished result",
							required = true),
			},
			responses = {
					@ApiResponse(responseCode = "200", description = "successful found results", ref = "SearchResult"),
					@ApiResponse(responseCode = "403", description = "tenant can't be accessed or does not exist"),
					@ApiResponse(responseCode = "404", description = "no result", ref = "SearchResult")
			})
	public SearchResult search(String tenant, SearchQuery searchQuery) throws IOException;

	@GET
	@Path("tenants")
	@ApiResponse(responseCode = "200", description = "a list of available tenants")
	public String[] getTenants();

}
