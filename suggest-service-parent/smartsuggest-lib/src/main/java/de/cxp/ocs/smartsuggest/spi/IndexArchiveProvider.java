package de.cxp.ocs.smartsuggest.spi;

import java.io.IOException;

public interface IndexArchiveProvider extends AbstractDataProvider<IndexArchive> {

	void store(String indexName, IndexArchive archive) throws IOException;

}
