package de.cxp.ocs.api;

import java.util.List;

import de.cxp.ocs.model.suggest.Suggestion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@SecurityScheme(name = "basic-auth", type = SecuritySchemeType.HTTP, scheme = "basic")
@SecurityRequirement(name = "basic-auth")
@Server(url = "http://suggest-service")
@Tag(name = "Suggest")
@Path("suggest-api/v1")
public interface SuggestService {

	@GET
	@Path("{indexname}/suggest")
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
	List<Suggestion> suggest(
			@Parameter(
					in = ParameterIn.PATH,
					name = "indexname",
					description = "index name that should be searched for autocompletions",
					required = true) String index,
			@Parameter(
					in = ParameterIn.QUERY,
					name = "userQuery",
					description = "the simple raw query typed by the user",
					required = true) String userQuery,
			@Parameter(
					in = ParameterIn.QUERY,
					name = "limit",
					description = "A optional limit for the suggestions",
					required = false) Integer limit,
			@Parameter(
					in = ParameterIn.QUERY,
					name = "filter",
					description = "Optional comma-separated list of filter values.",
					required = false) String filter)
			throws Exception;

}
