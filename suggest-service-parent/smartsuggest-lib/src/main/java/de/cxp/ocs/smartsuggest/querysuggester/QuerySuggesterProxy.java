package de.cxp.ocs.smartsuggest.querysuggester;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;

import de.cxp.ocs.smartsuggest.monitoring.Instrumentable;
import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import de.cxp.ocs.smartsuggest.util.Util;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QuerySuggesterProxy implements QuerySuggester, Instrumentable, Accountable {

	private final String	indexName;
	private final String	dataProviderName;
	private int				maxSuggestionsPerCacheEntry	= 10;

	private final AtomicReference<QuerySuggester>	innerQuerySuggester			= new AtomicReference<>(new NoopQuerySuggester());
	private volatile boolean						isClosed					= false;

	private final int						cacheLetterLength	= Integer.getInteger("CACHE_LETTER_LENGTH", 3);
	private Cache<String, List<Suggestion>>	firstLetterCache	= CacheBuilder.newBuilder()
			.maximumSize(Long.getLong("CACHE_MAX_SIZE", 10_000L))
			.build();

	private CacheStats	cacheStats;
	private long		lastCacheStatsUpdate;

	/**
	 * names for logging and metrics
	 * 
	 * @param indexName
	 * @param dataProviderName
	 */
	public QuerySuggesterProxy(String indexName, String dataProviderName) {
		this.indexName = indexName;
		this.dataProviderName = dataProviderName;
	}

	public QuerySuggesterProxy(String indexName, String dataType, int maxSuggestionsPerCacheEntry) {
		this(indexName, dataType);
		this.maxSuggestionsPerCacheEntry = maxSuggestionsPerCacheEntry;
	}

	public void updateQueryMapper(@NonNull QuerySuggester newSuggester) throws AlreadyClosedException {
		if (isClosed) throw new AlreadyClosedException("suggester for tenant " + indexName + " closed");
		if (log.isDebugEnabled()) {
			log.debug("updating from {} to {} for tenant {} with data from {}",
					innerQuerySuggester.get().getClass().getSimpleName(),
					newSuggester.getClass().getSimpleName(),
					indexName,
					dataProviderName);
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

	@Override
	public void setMetricsRegistryAdapter(Optional<MeterRegistryAdapter> metricsRegistryAdapter) {
		metricsRegistryAdapter.ifPresent(adapter -> addSensors(adapter.getMetricsRegistry()));
	}

	CacheStats getCacheStats() {
		if (cacheStats == null || lastCacheStatsUpdate + 60_000 < System.currentTimeMillis()) {
			cacheStats = firstLetterCache.stats();
			lastCacheStatsUpdate = System.currentTimeMillis();
		}
		return cacheStats;
	}

	private void addSensors(MeterRegistry reg) {
		Iterable<Tag> indexTag = Tags
				.of("indexName", indexName)
				.and("dataProvider", dataProviderName);
		reg.gauge(Util.APP_NAME + ".suggester.cache.hit_count", indexTag, this, me -> me.getCacheStats().hitCount());
		reg.gauge(Util.APP_NAME + ".suggester.cache.load_count", indexTag, this, me -> me.getCacheStats().loadCount());
		reg.gauge(Util.APP_NAME + ".suggester.cache.miss_count", indexTag, this, me -> me.getCacheStats().missCount());
		reg.gauge(Util.APP_NAME + ".suggester.cache.request_count", indexTag, this, me -> me.getCacheStats().requestCount());
	}

	@Override
	public long ramBytesUsed() {
		long mySize = RamUsageEstimator.shallowSizeOf(this);
		mySize += RamUsageEstimator.shallowSizeOf(firstLetterCache);
		mySize += RamUsageEstimator.sizeOfMap(firstLetterCache.asMap());
		QuerySuggester delegate = innerQuerySuggester.get();
		if (delegate instanceof Accountable) {
			mySize += RamUsageEstimator.sizeOf((Accountable) delegate);
		}
		return mySize;
	}
}
