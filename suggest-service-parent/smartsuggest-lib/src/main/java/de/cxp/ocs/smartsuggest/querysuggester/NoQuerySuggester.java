package de.cxp.ocs.smartsuggest.querysuggester;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.Set;

public class NoQuerySuggester implements QuerySuggester {

	@Override
	public void close() throws Exception {}

	@Override
	public List<Suggestion> suggest(String term, int maxResults, Set<String> groups) throws SuggestException {
		return emptyList();
	}
}
