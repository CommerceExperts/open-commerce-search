package de.cxp.ocs.api.indexer;

import javax.ws.rs.DELETE;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import de.cxp.ocs.model.index.Document;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

@OpenAPIDefinition(
		servers = @Server(url = "http://indexer"),
		tags = { @Tag(name = "index") })
@Path("update/{indexName}")
public interface UpdateIndexService {

	/**
	 * <p>
	 * Partial update of an existing document. If the document does not exist,
	 * no update will be performed and 'false' is returned.
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
	 * @param p
	 * @return
	 */
	@PATCH
	boolean patchDocument(String indexName, Document doc);

	/**
	 * Puts a document to the index. If document does not exist, it will be
	 * added.
	 * 
	 * An existing product will be overwritten unless the parameter
	 * "replaceExisting" is set to "false".
	 * 
	 * Provided document should be a complete object, partial updates should be
	 * done using the updateDocument method.
	 * 
	 * @param indexName
	 * @param doc
	 * @param replaceExisting
	 * @return true, if product was replaced or added.
	 */
	@POST
	boolean putProduct(String indexName, Document doc, boolean replaceExisting);


	/**
	 * Delete existing document. If document does not exist, it returns false.
	 * 
	 * @param indexName
	 * @param p
	 * @return
	 */
	@DELETE
	boolean deleteProduct(String indexName, String id);

}
