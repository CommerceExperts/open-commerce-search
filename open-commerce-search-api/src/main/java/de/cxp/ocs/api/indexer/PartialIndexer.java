package de.cxp.ocs.api.indexer;

import de.cxp.ocs.model.index.Document;

public interface PartialIndexer {

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
	boolean updateDocument(String indexName, Document doc);

	/**
	 * Complete replacement of an existing document. If document does not exist,
	 * it will be added.
	 * 
	 * Provided document should be a complete object, partial updates should be
	 * done using the updateDocument method.
	 * 
	 * @param indexName
	 * @param p
	 * @return true, if product was replaced or added.
	 */
	boolean replaceProduct(String indexName, Document doc);

	/**
	 * Add non existing document. If document does exist, this request fails and
	 * returns false.
	 * 
	 * @param indexName
	 * @param p
	 * @return
	 */
	boolean addProduct(String indexName, Document doc);

	/**
	 * Delete existing document. If document does not exist, it returns false.
	 * 
	 * @param indexName
	 * @param p
	 * @return
	 */
	boolean deleteProduct(String indexName, String id);

}
