package io.searchhub.smartsuggest.querysuggester.dhiman;

import java.util.Collection;

import com.search.suggestion.engine.SearchEngine;
import com.search.suggestion.text.analyze.SuggestAnalyzer;

import io.searchhub.smartsuggest.querysuggester.QuerySuggester;
import io.searchhub.smartsuggest.querysuggester.SuggesterFactory;
import io.searchhub.smartsuggest.spi.SuggestData;
import io.searchhub.smartsuggest.spi.SuggestRecord;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DhimanQuerySuggesterFactory implements SuggesterFactory {

	@Override
	public QuerySuggester getSuggester(SuggestData suggestData) {
		SearchEngine<SearchhubSuggest> searchEngine = new SearchEngine.Builder<SearchhubSuggest>()
				.setIndex(new SearchhubSuggestAdapter())
				.setAnalyzer(new SuggestAnalyzer())
				.build();

		Collection<SuggestRecord> suggestions = suggestData.getSuggestRecords();
		
		long start = System.currentTimeMillis();
		searchEngine.addAll(suggestions.stream().map(SearchhubSuggest::new));
		log.info("indexed {} suggestions in {}ms", suggestions.size(), (System.currentTimeMillis() - start));

		return new DhimanQuerySuggester(searchEngine);
	}

}
