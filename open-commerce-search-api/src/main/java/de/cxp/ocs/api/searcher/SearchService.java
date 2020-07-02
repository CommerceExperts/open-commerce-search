package de.cxp.ocs.api.searcher;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.SearchResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

@SecurityScheme(name = "basic-auth", type = SecuritySchemeType.HTTP, scheme = "basic")
@SecurityRequirement(name = "basic-auth")
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
	 * @param filters
	 *        Any other parameters are used as filters. They are validated
	 *        according to the actual data and configuration.
	 * 
	 *        Each filter can have multiple values, separated by comma. Commas
	 *        inside the values have to be double-URL encoded.
	 *        Depending on the configured backend type these values are used
	 *        differently.
	 * 
	 *        Examples:
	 *        <ul>
	 *        <li>
	 *        brand=adidas
	 *        </li>
	 *        <li>
	 *        brand=adidas,nike (=> products from adidas OR nike are shown)
	 *        </li>
	 *        <li>
	 *        category=men,shoes,sneaker (=> if category would be configured as
	 *        path, these values are used for hierarchical filtering)
	 *        </li>
	 *        <li>
	 *        price=10,99.99 (=> if price is configured as numeric field, these
	 *        values are used as range filters)
	 *        </li>
	 *        <li>
	 *        color=red,black (=> if that field is configured to be used for
	 *        "exclusive filtering" only products would be shown that are
	 *        available in red AND black)
	 *        </li>
	 *        <li>
	 *        optional for the future also negations could be supported, e.g.
	 *        color=red,!black
	 *        </li>
	 * 
	 * @return
	 * @throws Exception 
	 */
	@GET
	@Path("search/{tenant}")
	@Operation(
			summary = "Search for documents",
			description = "Runs a search request for a certain tenant."
					+ " The tenant should exist at the service and linked to a certain index in the backend."
					+ " Different tenants may use the same index.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "successful found results",
							content = @Content(schema = @Schema(ref = "SearchResult"))),
					@ApiResponse(
							responseCode = "204",
							description = "Optional response code that represents 'no result'",
							content = @Content(schema = @Schema(ref = "SearchResult"))),
					@ApiResponse(
							responseCode = "403", 
							description = "tenant can't be accessed or does not exist",
							content = @Content(mediaType = "text/plain")),
					@ApiResponse(
							responseCode = "404",
							description = "response code if tenant is unknown or index does not exist",
							content = @Content(mediaType = "text/plain"))
			})
	public SearchResult search(
			@Parameter(
					in = ParameterIn.PATH,
					name = "tenant",
					description = "tenant name",
					required = true) String tenant,
			@Parameter(
					in = ParameterIn.QUERY,
					name = "searchQuery",
					explode = Explode.TRUE,
					style = ParameterStyle.FORM,
					description = "the query that describes the wished result",
					required = true) SearchQuery searchQuery,
			@Parameter(
					in = ParameterIn.QUERY,
					name = "filters",
					description = "Any other parameters are used as filters. They are validated according to the actual data and configuration. " +
							"Each filter can have multiple values, separated by comma. Commas inside the values have to be double-URL encoded. " +
							"Depending on the configured backend type these values are used differently.",
					examples = {
							@ExampleObject(
									name = "simple filter", 
									value = "brand=adidas",
									description = "Filters are simple parameters with the field-names as parameter and the filter values as comma separated parameter values."),
							@ExampleObject(
									name = "joined filter", 
									value = "brand=adidas,nike", 
									description = "products from adidas OR nike are shown"),
							@ExampleObject(
									name = "hierarchical filter",
									value = "category=men,shoes,sneaker",
									description = "if category would be configured as path, these values are used for hierarchical filtering"),
							@ExampleObject(
									name = "numeric range filter",
									value = "price=10,99.90",
									description = "if price is configured as numeric field, these values are used as range filters"),
							@ExampleObject(
									name = "joined filter 2",
									value = "color=red,black",
									description = "The way filters are interpreted depends on the backend configuration. "
											+ "If that field is configured to be used for \"exclusive filtering\" only products would be shown that are available in red AND black")
					},
					explode = Explode.TRUE,
					style = ParameterStyle.FORM,
					required = false) Map<String, String> filters)
			throws Exception;

	@GET
	@Path("tenants")
	@ApiResponse(responseCode = "200", description = "a list of available tenants")
	public String[] getTenants();

}
