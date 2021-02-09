package de.cxp.ocs.smartsuggest.spi;

public class DummySuggestDataProvider implements SuggestDataProvider {

	@Override
	public boolean hasData(String indexName) {
		return false;
	}

	@Override
	public long getLastDataModTime(String indexName) {
		return -1;
	}

	@Override
	public SuggestData loadData(String indexName) {
		return null;
	}

}
