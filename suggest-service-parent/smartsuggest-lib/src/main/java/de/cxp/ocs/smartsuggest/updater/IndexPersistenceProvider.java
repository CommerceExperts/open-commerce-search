package de.cxp.ocs.smartsuggest.updater;

import java.io.IOException;

public interface IndexPersistenceProvider {

	/**
	 * <p>
	 * Respond with 'true' if this provider has a persisted version
	 * for the requested index. This is a quick check when initializing the
	 * suggesters, so it should not take too long.
	 * </p>
	 * <p>
	 * It's also possible to return a static "true" here and do the expensive
	 * availability check at the getLastDataModTime method, which is called
	 * async. If getLastDataModTime returns a value &lt; 0, data update are
	 * canceled as well.
	 * </p>
	 *
	 * @param indexName
	 *        identifier for the requested data
	 * @return if data is available
	 */
	boolean hasData(String indexName);

	/**
	 * <p>
	 * Get the timestamp from when the persisted version was modified the last time.
	 * For every change of that timestamp, the data will be pulled to build the
	 * suggest index.
	 * </p>
	 * <p>
	 * Setting the timestamp at the data is optional, but if it is set
	 * there, it MUST be the same timestamp, otherwise the data is rejected.
	 * This feature is used to avoid potential concurrency issues.
	 * </p>
	 * <p>
	 * If data is not available at all, a value &lt; 0 should be returned.
	 * </p>
	 *
	 * @param indexName
	 *        identifier for the requested data
	 * @return unix timestamp in millis or -1 if no data exists
	 * @throws IOException
	 *         if resource is not available
	 */
	long getLastModTime(String indexName) throws IOException;

	IndexArchive load(String indexName) throws IOException;

	void store(String indexName, IndexArchive archive) throws IOException;
}
