package de.cxp.ocs.api.indexer;

import java.util.List;
import java.util.Map;

import javax.ws.rs.*;

import de.cxp.ocs.model.index.Document;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

@SecurityScheme(name = "basic-auth", type = SecuritySchemeType.HTTP, scheme = "basic")
@SecurityRequirement(name = "basic-auth")
@Server(url = "http://indexer-service")
@Tag(name = "update")
@Path("indexer-api/v1/update/{indexName}")
public interface UpdateIndexService {

	// similar to org.elasticsearch.action.DocWriteResponse.Result
	public enum Result {
		CREATED, UPDATED, DELETED, NOT_FOUND, NOOP,
		/**
		 * Used if the update was not done due to a negative precondition.
		 */
		DISMISSED
	}

	/**
	 * <p>
	 * Partial update of an existing document. If the document does not exist,
	 * no update will be performed and status 404 is returned.
	 * </p>
	 * <p>
	 * In case the document is a master product with variants, the provided
	 * master product may only contain the changed values. However if
	 * some data at the product variants are updated, all data from all variant
	 * products are required, otherwise missing variants won't be there after
	 * the update!
	 * </p>
	 * 
	 * @param indexName
	 *        name of the index that should receive that update
	 * @param docs
	 *        Full or partial document that carries the data for the update
	 * @return Result code, one of UPDATED, NOT_FOUND, NOOP, DISMISSED
	 */
	@PATCH
	@Operation(
			description = "Partial update of existing documents."
					+ " If a document does not exist, no update will be performed and it gets the result status 'NOT_FOUND'."
					+ " In case a document is a master product with variants, the provided master product may only contain the changed values."
					+ " However if some of the variants should be updated, all data from all variant products are required,"
					+ " unless you have an ID data-field inside variant - then you can update single variants."
					+ " Without variant ID field, the missing variants won't be there after the update!"
					+ " This is how single variants can be deleted.",
			responses = {
					@ApiResponse(responseCode = "200", description = "OK. The response contains a map of ids and according result."),
					@ApiResponse(responseCode = "404", description = "index does not exist")
			})
	Map<String, Result> patchDocuments(@PathParam("indexName") String indexName, @RequestBody List<Document> docs);


	/**
	 * <p>
	 * Puts a document to the index. If document does not exist, it will be
	 * added.
	 * </p>
	 * <p>
	 * An existing product will be overwritten unless the parameter
	 * "replaceExisting" is set to "false".
	 * </p>
	 * <p>
	 * Provided document should be a complete object, partial updates should be
	 * done using the updateDocument method.
	 * </p>
	 * 
	 * @param indexName
	 *        name of the index that should receive that update
	 * @param docs
	 *        The documents that should be added or updated at the index.
	 * @param replaceExisting
	 *        set to false to avoid overriding a document with that ID. Defaults
	 *        to 'true'
	 * @return Result code, one of CREATED, UPDATED, NOOP, DISMISSED
	 */
	@PUT
	@Operation(
			description = "Puts a document to the index. If document does not exist, it will be added, but in that case the langCode parameter is required."
					+ " An existing product will be overwritten unless the parameter 'replaceExisting\" is set to \"false\"."
					+ " Provided document should be a complete object, partial updates should be done using the updateDocument method.",
			responses = {
					@ApiResponse(responseCode = "200", description = "OK. The response contains a map of ids and according result."),
					@ApiResponse(responseCode = "404", description = "index does not exist"),
			})
	Map<String, Result> putDocuments(
			@Parameter(
					in = ParameterIn.PATH,
					name = "indexName",
					required = true) String indexName,
			@Parameter(
					in = ParameterIn.QUERY,
					name = "replaceExisting",
					description = "set to false to avoid overriding a document with that ID. Defaults to 'true'",
					required = false) Boolean replaceExisting,
			@Parameter(
					in = ParameterIn.QUERY,
					name = "langCode",
					description = "If this put request targets an index that does not exist yet, that index will be created. "
							+ "To use the correct index template, the language is required for that case. Otherwise its ignored.",
					required = false)
			String langCode,
			@RequestBody List<Document> docs);

	/**
	 * Delete existing document. If document does not exist, it returns code
	 * 404.
	 * 
	 * @param indexName
	 *        name of the index that should receive that update
	 * @param ids
	 *        Array of IDs of the documents that should be deleted
	 * @return Result code, one of DELETED, NOT_FOUND
	 */
	@DELETE
	@Operation(
			description = "Delete existing document. If document does not exist, it returns code 304.",
			responses = {
					@ApiResponse(responseCode = "200", description = "OK. The response contains a map of ids and according result."),
					@ApiResponse(responseCode = "404", description = "index does not exist")
			})
	Map<String, Result> deleteDocuments(@PathParam("indexName") String indexName, @QueryParam("id[]") List<String> ids);

}
