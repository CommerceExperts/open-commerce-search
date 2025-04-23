package de.cxp.ocs.smartsuggest;

import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestDataProvider;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
@Accessors(fluent = true)
@Setter
public class SdpMock implements SuggestDataProvider {

	@NonNull
	private final String indexName;
	private SuggestData suggestData = null;

	@Override
	public boolean hasData(String indexName) {
		return this.indexName.equals(indexName) && suggestData != null;
	}

	@Override
	public long getLastDataModTime(String indexName) {
		return suggestData == null ? -1L : suggestData.getModificationTime();
	}

	@Override
	public SuggestData loadData(String indexName) {
		return suggestData;
	}
}
