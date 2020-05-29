package de.cxp.ocs.smartsuggest.querysuggester.dhiman;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.search.suggestion.data.SearchPayload;
import com.search.suggestion.engine.SearchEngine;

import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.SuggestException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DhimanQuerySuggester implements QuerySuggester {

	@NonNull
	private final SearchEngine<SearchhubSuggest> searchEngine;
	private Comparator<SearchhubSuggest> searchSuggestWeightComparator = new Comparator<SearchhubSuggest>() {

		@Override
		public int compare(SearchhubSuggest o1, SearchhubSuggest o2) {
			// highest weight first
			return Double.compare(o2.getWeight(), o1.getWeight());
		}};

	@Override
	public List<Result> suggest(String term, int maxResults, Set<String> groups) throws SuggestException {
		SearchPayload searchPayload = new SearchPayload();
		searchPayload.setSearch(term);
		searchPayload.setFilter(Collections.emptyMap());
		searchPayload.setBucket(Collections.emptyMap());
		searchPayload.setLimit(maxResults);

		List<SearchhubSuggest> search = searchEngine.search(searchPayload);
		Result result = new Result("default", search.stream()
				.sorted(searchSuggestWeightComparator)
				.map(SearchhubSuggest::getBestQuery)
				.limit(maxResults)
				.collect(Collectors.toList()));
		return Collections.singletonList(result);
	}

	@Override
	public void close() throws Exception {

	}

}
