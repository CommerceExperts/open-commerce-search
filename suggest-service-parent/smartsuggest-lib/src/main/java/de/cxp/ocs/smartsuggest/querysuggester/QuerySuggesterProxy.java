package de.cxp.ocs.smartsuggest.querysuggester;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.store.AlreadyClosedException;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QuerySuggesterProxy implements QuerySuggester {

	private final AtomicReference<QuerySuggester>	innerQuerySuggester			= new AtomicReference<>(new NoopQuerySuggester());
	private final String							indexName;
	private volatile boolean						isClosed					= false;
	private int										maxSuggestionsPerCacheEntry	= 10;

	private final int					cacheLetterLength	= Integer.getInteger("CACHE_LETTER_LENGTH", 3);
	private Cache<String, List<Suggestion>>	firstLetterCache	= CacheBuilder.newBuilder()
			.maximumSize(Long.getLong("CACHE_MAX_SIZE", 10_000L))
			.build();

	public QuerySuggesterProxy(String indexName) {
		this.indexName = indexName;
	}

	public QuerySuggesterProxy(String indexName, int maxSuggestionsPerCacheEntry) {
		this(indexName);
		this.maxSuggestionsPerCacheEntry = maxSuggestionsPerCacheEntry;
	}

	public void updateQueryMapper(@NonNull QuerySuggester newSuggester) throws AlreadyClosedException {
		if (isClosed) throw new AlreadyClosedException("suggester for tenant " + indexName + " closed");
		if (log.isDebugEnabled()) {
			log.debug("updating from {} to {} for tenant {}",
					innerQuerySuggester.get().getClass().getSimpleName(),
					newSuggester.getClass().getSimpleName(),
					indexName);
		}
		firstLetterCache.asMap().keySet()
				.forEach(term -> firstLetterCache.put(term, newSuggester.suggest(term)));
		QuerySuggester oldSuggester = innerQuerySuggester.getAndSet(newSuggester);
		if (oldSuggester != null) {
			oldSuggester.destroy();
		}
	}

	@Override
	public boolean isReady() {
		return innerQuerySuggester.get().isReady();
	}

	@Override
	public void close() throws Exception {
		isClosed = true;
		firstLetterCache.invalidateAll();
		firstLetterCache.cleanUp();
		innerQuerySuggester.get().close();
	}

	@Override
	public List<Suggestion> suggest(String term, int maxResults, Set<String> tags) throws SuggestException {
		if (isClosed || isBlank(term)) return emptyList();
		final String normalizedTerm = term.trim().toLowerCase();

		// only cache results, if no tags filter is given and the the limit
		// of results is <= to the maxSuggestionPerCacheEntry level
		if (normalizedTerm.length() <= cacheLetterLength
				&& (tags == null || tags.isEmpty())
				&& maxResults <= maxSuggestionsPerCacheEntry) {
			try {
				List<Suggestion> cachedResults = firstLetterCache.get(normalizedTerm,
						() -> innerQuerySuggester.get().suggest(normalizedTerm, maxSuggestionsPerCacheEntry, tags));

				if (maxResults < maxSuggestionsPerCacheEntry) {
					cachedResults = cachedResults.subList(0, maxResults);
				}

				return cachedResults;
			}
			catch (ExecutionException e) {
				throw new SuggestException(e.getCause());
			}
		}
		else {
			return innerQuerySuggester.get().suggest(normalizedTerm, maxResults, tags);
		}
	}
}
