package de.cxp.ocs.smartsuggest.spi;

import java.io.IOException;
import java.util.Map;

/**
 * General common interface for the different data providers.
 * They are all used by the same principles:
 * First they are instantiated and configured, then asked if they have data for a specific index.
 * If that's the case, data is pulled initially and then regularly whenever 'getLastDataModTime'
 * returns a newer timestamp.
 *
 * @param <T>
 * 		the provided data type
 */
public interface AbstractDataProvider<T> {

	/**
	 * <p>
	 * This method is always called directly after instantiating the data provider
	 * with its no-args-constructor. The given configuration is passed through the
	 * QuerySuggestManager and might be empty.
	 * </p>
	 * <p>
	 * If this data provider is unusable due to missing
	 * configuration, it should throw an Exception, so it will be dropped
	 * </p>
	 *
	 * @param config
	 * 		specific data provider configuration (never null, but may be empty)
	 */
	default void configure(Map<String, Object> config) {
	}

	/**
	 * <p>
	 * Respond with 'true' if this provider is generally able to provide data
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
	 * 		identifier for the requested data
	 * @return if data is available
	 */
	boolean hasData(String indexName);

	/**
	 * <p>
	 * Get the timestamp from when the data was modified the last time. For
	 * every change of that timestamp, the data will be pulled and indexed into
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
	 * 		identifier for the requested data
	 * @return unix timestamp in millis
	 * @throws IOException
	 * 		if resource is not available
	 */
	long getLastDataModTime(String indexName) throws IOException;

	/**
	 * @param indexName
	 * 		identifier for the requested data
	 * @return suggest data
	 * @throws IOException
	 * 		if data couldn't be loaded
	 */
	T loadData(String indexName) throws IOException;

}
