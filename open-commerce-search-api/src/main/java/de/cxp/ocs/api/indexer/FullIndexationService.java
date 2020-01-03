package de.cxp.ocs.api.indexer;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import de.cxp.ocs.model.index.BulkImportData;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@OpenAPIDefinition(
		servers = @Server(url = "http://indexer"),
		tags = { @Tag(name = "index") })
@Path("full")
public interface FullIndexationService {

	/**
	 * Start a new full import. Returns a handle containing meta data, that has
	 * to be passed to all following calls.
	 * 
	 * @param indexName
	 * @return
	 * @throws IllegalStateException
	 *         in case there is already a full-import running for that index.
	 */
	@GET
	@Path("start/{indexName}")
	@Operation(
			summary = "Starts a new full import",
			description = "Starts a new full import. Returns a handle containing meta data, that has "
					+ "to be passed to all following calls.",
			parameters = {
					@Parameter(
							in = ParameterIn.PATH,
							name = "indexName",
							description = "index name, that should match the regular expression '[a-z0-9_-]+'",
							required = true),
					@Parameter(
							in = ParameterIn.QUERY,
							name = "locale",
							description = "used for language dependent settings",
							required = true)
			},
			responses = {
					@ApiResponse(responseCode = "200", description = "import session started", ref = "ImportSession"),
					@ApiResponse(responseCode = "409", description = "there is already an import running for that index")
			})
	ImportSession startImport(String indexName, String locale)
			throws IllegalStateException;

	/**
	 * Add one or more documents to a running import session.
	 * 
	 * @param session
	 * @param p
	 */
	@POST
	@Path("add")
	@Operation(
			summary = "Add documents to a running import session",
			description = "Add one or more documents to a running import session.",
			requestBody = @RequestBody(
					description = "Data that contains the import session reference and one or more documents that should be added to that session.",
					ref = "BulkImportData",
					required = true),
			responses = {
					@ApiResponse(responseCode = "204", description = "products successfuly added"),
					@ApiResponse(responseCode = "404", description = "according import session does not exist")
			})
	void add(BulkImportData data) throws Exception;

	/**
	 * Finishes the import, flushing the new index and (in case there is
	 * already an index with the initialized name) replacing the old one.
	 * 
	 * @return
	 */
	@POST
	@Path("done")
	boolean done(@RequestBody ImportSession session) throws Exception;

	/**
	 * cancels import which results in a deletion of the temporary index.
	 * 
	 * @param session
	 * @return
	 */
	@POST
	@Path("cancel")
	boolean cancel(@RequestBody ImportSession session);

}
