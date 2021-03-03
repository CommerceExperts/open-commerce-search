package de.cxp.ocs.smartsuggest.querysuggester;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class NoopQuerySuggester implements QuerySuggester {

	private boolean isReady = false;

	@Override
	public void close() throws Exception {}

	@Override
	public List<Suggestion> suggest(String term, int maxResults, Set<String> tags) throws SuggestException {
		return emptyList();
	}

	@Override
	public boolean isReady() {
		return isReady;
	}

	@Override
	public long recordCount() {
		return 0;
	}
}
