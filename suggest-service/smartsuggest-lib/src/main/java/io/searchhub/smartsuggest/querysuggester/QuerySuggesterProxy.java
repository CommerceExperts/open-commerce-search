package io.searchhub.smartsuggest.querysuggester;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.store.AlreadyClosedException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QuerySuggesterProxy implements QuerySuggester {

	private final AtomicReference<QuerySuggester>	innerQuerySuggester	= new AtomicReference<>(new NoQuerySuggester());
	private final String							indexName;
	private volatile boolean						isClosed			= false;

	private final int cacheLetterLength = Integer.getInteger("CACHE_LETTER_LENGTH", 3);
	private LoadingCache<String, List<Result>> firstLetterCache = CacheBuilder.newBuilder()
			.maximumSize(Long.getLong("CACHE_MAX_SIZE", 10_000L))
			.build(new CacheLoader<String, List<Result>>() {

				@Override
				public List<Result> load(String key) throws Exception {
					return innerQuerySuggester.get().suggest(key);
				}});
	
	public QuerySuggesterProxy(String indexName) {
		this.indexName = indexName;
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
	public void close() throws Exception {
		isClosed = true;
		firstLetterCache.invalidateAll();
		firstLetterCache.cleanUp();
		innerQuerySuggester.get().close();
	}

	@Override
	public List<Result> suggest(String term, int maxResults, Set<String> groups) throws SuggestException {
		if (isClosed || isBlank(term)) return emptyList();
		term = term.trim().toLowerCase();
		if (term.length() <= cacheLetterLength) {
			try {
				return firstLetterCache.get(term);
			}
			catch (ExecutionException e) {
				throw new SuggestException(e.getCause());
			}
		} else {
			return innerQuerySuggester.get().suggest(term, maxResults, groups);
		}
	}
}
