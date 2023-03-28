package de.cxp.ocs.smartsuggest.querysuggester;

import java.util.List;
import java.util.Set;

import de.cxp.ocs.smartsuggest.limiter.Limiter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
public class GroupingSuggester implements QuerySuggester {

	private final QuerySuggester	delegate;
	private final Limiter			limiter;

	@Setter
	@Accessors(chain = true)
	private int prefetchLimitFactor = 1;

	@Override
	public List<Suggestion> suggest(String term, int maxResults, Set<String> tags) throws SuggestException {
		List<Suggestion> fetchedResults = delegate.suggest(term, maxResults * prefetchLimitFactor, tags);
		return limiter.limit(fetchedResults, maxResults);
	}

	@Override
	public long recordCount() {
		return delegate.recordCount();
	}

	@Override
	public boolean isReady() {
		return delegate.isReady();
	}

	@Override
	public void close() throws Exception {
		delegate.close();
	}
}
