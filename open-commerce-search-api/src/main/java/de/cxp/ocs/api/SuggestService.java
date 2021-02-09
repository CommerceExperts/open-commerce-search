package de.cxp.ocs.api;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import de.cxp.ocs.model.suggest.Suggestion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

@SecurityScheme(name = "basic-auth", type = SecuritySchemeType.HTTP, scheme = "basic")
@SecurityRequirement(name = "basic-auth")
@Server(url = "http://suggest-service")
@Tag(name = "search")
@Path("suggest-api/v1")
public interface SuggestService {

	@GET
	@Path("suggest/{indexname}")
	@Operation(
			summary = "Autocomplete the user input",
			description = "Runs a suggestion request on the data of a certain index.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "successful found results",
							content = @Content(array = @ArraySchema(schema = @Schema(ref = "Suggestion")))),
					@ApiResponse(
							responseCode = "404",
							description = "tenant is unknown or index does not exist",
							content = @Content(mediaType = "text/plain"))
			})
	public List<Suggestion> suggest(
			@Parameter(
					in = ParameterIn.PATH,
					name = "index",
					description = "index name that should be searched for autocompletions",
					required = true) String index,
			@Parameter(
					in = ParameterIn.QUERY,
					name = "userQuery",
					description = "the simple raw query typed by the user",
					required = true) String userQuery,
			@Parameter(
					in = ParameterIn.QUERY,
					name = "filters",
					description = "Any other parameter is used as filter.",
					explode = Explode.TRUE,
					style = ParameterStyle.FORM,
					required = false) Map<String, String> filters)
			throws Exception;

}
