package io.searchhub.smartsuggest.spi;

public interface SuggestDataProvider {

	boolean hasData(String indexName);

	/**
	 * If data is not available, -1 should be returned.
	 * 
	 * @param indexName
	 * @return timestamp in seconds
	 */
	long getLastDataModTime(String indexName);

	SuggestData loadData(String indexName);

}
