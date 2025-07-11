package de.cxp.ocs.smartsuggest.updater;

import de.cxp.ocs.smartsuggest.spi.CompoundIndexArchiveProvider;
import de.cxp.ocs.smartsuggest.spi.IndexArchive;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LocalCompoundIndexArchiveProvider extends CompoundIndexArchiveProvider {

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

	@Override
	public Collection<String> getIndexSuffixes(String indexName) {
		return cached.keySet().stream()
				.filter(key -> key.startsWith(indexName + "/"))
				.map(key -> key.substring(indexName.length() + 1))
				.toList();
	}
}
