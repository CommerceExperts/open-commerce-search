package de.cxp.ocs.smartsuggest.util;

import java.util.HashMap;
import java.util.Map;

import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestDataProvider;

public class TestDataProvider implements SuggestDataProvider {

	Map<String, Long>			modDates	= new HashMap<>();
	Map<String, SuggestData>	suggestData	= new HashMap<>();

	public TestDataProvider putData(String indexName, SuggestData data) {
		suggestData.put(indexName, data);
		modDates.put(indexName, data.getModificationTime() > 0 ? data.getModificationTime() : System.currentTimeMillis());
		return this;
	}

	@Override
	public boolean hasData(String indexName) {
		return suggestData.containsKey(indexName);
	}

	@Override
	public long getLastDataModTime(String indexName) {
		return modDates.getOrDefault(indexName, -1L);
	}

	@Override
	public SuggestData loadData(String indexName) {
		return suggestData.get(indexName);
	}

}
