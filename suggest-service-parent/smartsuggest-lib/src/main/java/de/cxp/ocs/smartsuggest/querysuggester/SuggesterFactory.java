package de.cxp.ocs.smartsuggest.querysuggester;

import de.cxp.ocs.smartsuggest.monitoring.Instrumentable;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestData;

import java.io.File;
import java.io.IOException;

public interface SuggesterFactory<T extends QuerySuggester> extends Instrumentable {

	T getSuggester(SuggestData suggestData, SuggestConfig suggestConfig);

	File persist(T querySuggester) throws IOException;

	T recover(File baseDir);

}
