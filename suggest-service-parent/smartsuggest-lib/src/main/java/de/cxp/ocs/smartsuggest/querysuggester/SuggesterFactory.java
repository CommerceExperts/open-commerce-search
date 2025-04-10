package de.cxp.ocs.smartsuggest.querysuggester;

import de.cxp.ocs.smartsuggest.monitoring.Instrumentable;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestData;

import java.io.IOException;
import java.nio.file.Path;

public interface SuggesterFactory<T extends QuerySuggester> extends Instrumentable {

	T getSuggester(SuggestData suggestData, SuggestConfig suggestConfig);

	Path persist(T querySuggester) throws IOException;

	T recover(Path baseDir, SuggestConfig suggestConfig) throws IOException;

}
