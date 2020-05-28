package io.searchhub.smartsuggest.querysuggester;

import io.searchhub.smartsuggest.spi.SuggestData;

public interface SuggesterFactory {

	QuerySuggester getSuggester(SuggestData suggestData);

}
