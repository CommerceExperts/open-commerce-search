package de.cxp.ocs.smartsuggest.updater;

import de.cxp.ocs.smartsuggest.spi.IndexArchive;
import de.cxp.ocs.smartsuggest.spi.IndexArchiveProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LocalIndexArchiveProvider implements IndexArchiveProvider {

	final private Map<String, IndexArchive> cached = new HashMap<>();

	@Override
	public void store(String indexName, IndexArchive archive) {
		cached.put(indexName, archive);
	}

	@Override
	public boolean hasData(String indexName) {
		return cached.containsKey(indexName);
	}

	@Override
	public long getLastDataModTime(String indexName) {
		return Optional.ofNullable(cached.get(indexName)).map(IndexArchive::dataModificationTime).orElse(-1L);
	}

	@Override
	public IndexArchive loadData(String indexName) {
		return cached.get(indexName);
	}

}
