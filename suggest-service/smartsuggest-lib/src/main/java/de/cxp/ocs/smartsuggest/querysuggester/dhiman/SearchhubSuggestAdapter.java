package de.cxp.ocs.smartsuggest.querysuggester.dhiman;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import com.search.suggestion.adaptor.IndexAdapter;
import com.search.suggestion.data.ScoredObject;
import com.search.suggestion.data.SearchPayload;
import com.search.suggestion.text.index.FuzzyIndex;
import com.search.suggestion.text.index.OptimizedTrie;
import com.search.suggestion.text.match.EditDistanceAutomaton;
import com.search.suggestion.text.match.PrefixAutomaton;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SearchhubSuggestAdapter implements IndexAdapter<SearchhubSuggest> {

	private FuzzyIndex<SearchhubSuggest>	index			= new OptimizedTrie<SearchhubSuggest>();
	private Boolean							isFuzzyEnabled	= false;
	private int								minChars		= 3;

	@Override
	public Collection<ScoredObject<SearchhubSuggest>> get(String token, SearchPayload json) {

		Set<ScoredObject<SearchhubSuggest>> results = new LinkedHashSet<>(json.getLimit());

		results.addAll(getPrefixMatches(token, json));

		if (isFuzzyEnabled && results.size() < json.getLimit() && token.length() > minChars) {
			results.addAll(getFuzzy(token, json));
		}

		return results;
	}

	private Collection<ScoredObject<SearchhubSuggest>> getPrefixMatches(String token, SearchPayload json) {
		return index.getAny(new PrefixAutomaton(token), json);
	}

	private Collection<ScoredObject<SearchhubSuggest>> getFuzzy(String token, SearchPayload json) {
		long threshold = Math.round(Math.log(token.length()));
		EditDistanceAutomaton eda = new EditDistanceAutomaton(token, threshold);
		return index.getAny(eda, json);
	}

	@Override
	public boolean put(String token, SearchhubSuggest value) {
		return index.put(token, value);
	}

	@Override
	public void setFaultTolerant(Boolean bool) {
		isFuzzyEnabled = bool;
	}
}
