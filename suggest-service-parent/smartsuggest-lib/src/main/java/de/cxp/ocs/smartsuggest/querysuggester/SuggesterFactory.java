package de.cxp.ocs.smartsuggest.querysuggester;

import de.cxp.ocs.smartsuggest.monitoring.Instrumentable;
import de.cxp.ocs.smartsuggest.spi.IndexArchive;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestData;

import java.io.IOException;
import java.nio.file.Path;

public interface SuggesterFactory<T extends QuerySuggester> extends Instrumentable {

	void init(Path baseDirectory);

	T getSuggester(SuggestData suggestData, SuggestConfig suggestConfig);

	IndexArchive createArchive(QuerySuggester querySuggester) throws IOException;

	T recover(IndexArchive baseDir, SuggestConfig suggestConfig) throws IOException;

}
