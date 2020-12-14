package de.cxp.ocs.api.indexer;

import javax.ws.rs.DELETE;
import javax.ws.rs.PATCH;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

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
	 * @param doc
	 *        Full or partial document that carries the data for the update
	 * @return true, if product was patched successful
	 */
	@PATCH
	@Operation(
			description = "Partial update of an existing document."
					+ " If the document does not exist, no update will be performed and status code 404 is returned."
					+ " In case the document is a master product with variants, the provided master product may only contain the changed values."
					+ " However if some data at the product variants are updated, all data from all variant products are required,"
					+ " otherwise missing variants won't be there after the update! This is how single variants can be deleted.",
			responses = {
					@ApiResponse(responseCode = "200", description = "document successfuly patched"),
					@ApiResponse(responseCode = "404", description = "index does not exist or document not found")
			})
	boolean patchDocument(@PathParam("indexName") String indexName, @RequestBody Document doc);

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
	 * @param doc
	 *        The document that should be added or updated at the index.
	 * @param replaceExisting
	 *        set to false to avoid overriding a document with that ID. Defaults
	 *        to 'true'
	 * @return true, if product was replaced or added.
	 */
	@PUT
	@Operation(
			description = "Puts a document to the index. If document does not exist, it will be added."
					+ " An existing product will be overwritten unless the parameter 'replaceExisting\" is set to \"false\"."
					+ " Provided document should be a complete object, partial updates should be  done using the updateDocument method.",
			responses = {
					@ApiResponse(responseCode = "201", description = "Document created"),
					@ApiResponse(responseCode = "404", description = "index does not exist"),
					@ApiResponse(responseCode = "409", description = "Document already exists but replaceExisting is set to false")
			})
	boolean putDocument(
			@Parameter(
					in = ParameterIn.PATH,
					name = "indexName",
					required = true) String indexName,
			@Parameter(
					in = ParameterIn.QUERY,
					name = "replaceExisting",
					description = "set to false to avoid overriding a document with that ID. Defaults to 'true'",
					required = false) Boolean replaceExisting,
			@RequestBody Document doc);


	/**
	 * Delete existing document. If document does not exist, it returns code
	 * 404.
	 * 
	 * @param indexName
	 *        name of the index that should receive that update
	 * @param id
	 *        ID of the document that should be deleted
	 * @return true if document was not found or deleted. If deletion fails for
	 *         some reason, false is returned.
	 */
	@DELETE
	@Operation(
			description = "Delete existing document. If document does not exist, it returns code 304.",
			responses = {
					@ApiResponse(responseCode = "200", description = "document deleted"),
					@ApiResponse(responseCode = "304", description = "document not found"),
					@ApiResponse(responseCode = "404", description = "index does not exist")
			})
	boolean deleteDocument(@PathParam("indexName") String indexName, @QueryParam("id") String id);

}
