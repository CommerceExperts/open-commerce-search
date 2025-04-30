package de.cxp.ocs.smartsuggest.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.Collection;

public abstract class CompoundIndexArchiveProvider implements IndexArchiveProvider {

	public abstract Collection<String> getIndexSuffixes(String indexName);

	public IndexArchiveProvider getSuffixProvider(final String suffix) {
		return new DefaultChildIndexArchiveProvider(this, suffix);
	}

	@RequiredArgsConstructor
	public static class DefaultChildIndexArchiveProvider implements IndexArchiveProvider {

		@NonNull final IndexArchiveProvider parent;
		@NonNull final String               suffix;

		private String combinedName(String indexName, String suffix) {
			return indexName + "/" + suffix;
		}

		@Override
		public void store(String indexName, IndexArchive archive) throws IOException {
			parent.store(combinedName(indexName, suffix), archive);
		}

		@Override
		public boolean hasData(String indexName) {
			return parent.hasData(combinedName(indexName, suffix));
		}

		@Override
		public long getLastDataModTime(String indexName) throws IOException {
			return parent.getLastDataModTime(combinedName(indexName, suffix));
		}

		@Override
		public IndexArchive loadData(String indexName) throws IOException {
			return parent.loadData(combinedName(indexName, suffix));
		}
	}
}
