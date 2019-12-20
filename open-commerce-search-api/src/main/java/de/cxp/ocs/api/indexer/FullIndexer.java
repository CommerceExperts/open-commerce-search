package de.cxp.ocs.api.indexer;

import java.util.Locale;

import de.cxp.ocs.model.index.Document;

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
public interface FullIndexer {

	/**
	 * Start a new full import. Returns a handle containing meta data, that has
	 * to be passed to all following calls.
	 * 
	 * @param indexName
	 * @return
	 * @throws IllegalStateException
	 *         in case there is already a full-import running for that index.
	 */
	ImportSession startImport(String indexName, Locale locale) throws IllegalStateException;

	/**
	 * Add one or more products to import session.
	 * 
	 * @param session
	 * @param p
	 */
	void addProducts(ImportSession session, Document[] doc) throws Exception;

	/**
	 * Finishes the import, flushing the new index and (in case there is
	 * already an index with the initialized name) replacing the old one.
	 * 
	 * @return
	 */
	boolean done(ImportSession session) throws Exception;

	/**
	 * cancels import which results in a deletion of the temporary index.
	 * 
	 * @param session
	 * @return
	 */
	boolean cancel(ImportSession session);

}
