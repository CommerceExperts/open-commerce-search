package de.cxp.ocs.smartsuggest.querysuggester;

import de.cxp.ocs.smartsuggest.spi.SuggestData;

public interface SuggesterFactory {

	QuerySuggester getSuggester(SuggestData suggestData);

}
