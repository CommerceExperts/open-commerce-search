package de.cxp.ocs.api.indexer;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import de.cxp.ocs.model.index.BulkImportData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Run a full import into a new index.
 * 
 * It is recommended to use the getIndexName method to retrieve a proper
 * localized name, which in turn is required to have the correct analyzer be
 * used for the indexed data.
 * 
 * If product and content data should be indexed, consider using different
 * indexes.
 * Otherwise make sure to use the same fields for the same content type,
 * e.g. both kind of documents can have a textual "title" field, but both
 * kind of documents shouldn't have for example an "author" field, which could
 * be used for product facets (e.g. book authors) but not for faceting the
 * content documents (e.g. blog post authors).
 */

@SecurityScheme(name = "basic-auth", type = SecuritySchemeType.HTTP, scheme = "basic")
@SecurityRequirement(name = "basic-auth")
@Server(
		url = "http://indexer-service",
		description = "Service to run a full import into a new index." +
				" To do so, start a indexation session with a request to 'start' and use the" +
				" returned ImportSession object to 'add' products bulkwise." +
				" If all documents where added, use the 'done' request to deploy that index." +
				" In case there were failures (or more failures then tollerated), the 'cancel' request" +
				" can be used to stop the process and cleanup the incomplete index." +
				" Depending on the document size, an amount of 500-2000 documents per bulk is sufficient." +
				" If product and content data should be indexed, its recommended to use different" +
				" indexes." +
				" Otherwise make sure to use the same fields for the same content type," +
				" e.g. both kind of documents can have a textual 'title' field, but both" +
				" kind of documents shouldn't have for example an 'author' field, which could" +
				" be used for product facets (e.g. book authors) but not for faceting the" +
				" content documents (e.g. blog post authors).")
@Tag(name = "indexer")
@Path("indexer-api/v1/full")
public interface FullIndexationService {

	/**
	 * Start a new full import. Returns a handle containing meta data, that has
	 * to be passed to all following calls.
	 * 
	 * @param indexName
	 *        index name, that should match the regular expression '[a-z0-9_-]+'
	 * @param locale
	 *        used for language dependent settings
	 * @return
	 *         {@link ImportSession} that should be used for follow up requests
	 *         to add data to that new index
	 * @throws IllegalStateException
	 *         in case there is already a full-import running for that index.
	 */
	@GET
	@Path("start/{indexName}")
	@Operation(
			description = "Starts a new full import. Returns a handle containing meta data, that has "
					+ "to be passed to all following calls.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "import session started",
							content = @Content(schema = @Schema(ref = "ImportSession"))),
					@ApiResponse(responseCode = "409", description = "there is already an import running for that index")
			})
	ImportSession startImport(
			@Parameter(
					in = ParameterIn.PATH,
					name = "indexName",
					description = "index name, that should match the regular expression '[a-z0-9_-]+'",
					required = true) String indexName,
			@Parameter(
					in = ParameterIn.QUERY,
					name = "locale",
					description = "used for language dependent settings",
					required = true) String locale)
			throws IllegalStateException;

	/**
	 * Add one or more documents to a running import session.
	 * 
	 * @param data
	 *        bulk data which consist of the {@link ImportSession} and one or
	 *        more products that should be added to that index.
	 * @return the amount of documents that were successfully added to the index
	 * @throws Exception
	 *         in case import session is invalid
	 */
	@POST
	@Path("add")
	@Operation(
			description = "Add one or more documents to a running import session.",
			requestBody = @RequestBody(
					description = "Data that contains the import session reference and one or more documents that should be added to that session.",
					content = { @Content(schema = @Schema(ref = "BulkImportData")) },
					required = true),
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "documents successfully added",
							content = @Content(schema = @Schema(description = "Amount of documents successfuly added"))),
					@ApiResponse(responseCode = "400", description = "import session is invalid")
			})
	int add(BulkImportData data) throws Exception;

	/**
	 * Finishes the import, flushing the new index and (in case there is
	 * already an index with the initialized name) replacing the old one.
	 * 
	 * @param session
	 *        ImportSession that should be closed.
	 * @return true on success, otherwise false
	 * @throws Exception
	 *         if import session is invalid
	 */
	@POST
	@Path("done")
	@Operation(
			description = "Finishes the import, flushing the new index and (in case there is"
					+ " already an index with the initialized name) replacing the old one.",
			requestBody = @RequestBody(
					content = { @Content(schema = @Schema(ref = "ImportSession")) },
					required = true),
			responses = {
					@ApiResponse(responseCode = "200", description = "successfully done"),
					@ApiResponse(responseCode = "400", description = "indexation was already confirmed or import session is invalid")
			})
	boolean done(@RequestBody ImportSession session) throws Exception;

	/**
	 * Cancels import which results in a deletion of the temporary index.
	 * 
	 * @param session
	 *        ImportSession that contains the information, which index should be
	 *        dropped.
	 * @throws Exception
	 *         if import session is invalid
	 */
	@POST
	@Path("cancel")
	@Operation(
			description = "Cancels the import and in case there was an index created, it will be deleted.",
			requestBody = @RequestBody(
					content = { @Content(schema = @Schema(ref = "ImportSession")) },
					required = true),
			responses = {
					@ApiResponse(responseCode = "202"),
					@ApiResponse(responseCode = "400", description = "indexation was already confirmed or import session is invalid")
			})
	void cancel(@RequestBody ImportSession session) throws Exception;

}
