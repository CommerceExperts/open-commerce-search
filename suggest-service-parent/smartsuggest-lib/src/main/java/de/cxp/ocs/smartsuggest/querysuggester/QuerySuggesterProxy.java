package de.cxp.ocs.smartsuggest.querysuggester;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.HashMap;
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
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QuerySuggesterProxy implements QuerySuggester, Instrumentable, Accountable {

	private final String	indexName;
	private final String	dataProviderName;
	private int				maxSuggestionsPerCacheEntry	= 10;

	private final AtomicReference<QuerySuggester>	innerQuerySuggester	= new AtomicReference<>(new NoopQuerySuggester());
	private volatile boolean						isClosed			= false;

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
		final String normalizedTerm = term.toLowerCase();

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

				// dont use stream + collector, because we need a mutable list
				ArrayList<Suggestion> clonedList = new ArrayList<>(cachedResults.size());
				for (Suggestion cachedSuggestion : cachedResults) {
					clonedList.add(cloneSuggestion(cachedSuggestion));
				}
				return clonedList;
			}
			catch (ExecutionException e) {
				throw new SuggestException(e.getCause());
			}
		}
		else {
			return innerQuerySuggester.get().suggest(normalizedTerm, maxResults, tags);
		}
	}

	private Suggestion cloneSuggestion(Suggestion s) {
		Suggestion clone = new Suggestion(s.getLabel());
		clone.setTags(s.getTags());
		clone.setPayload(s.getPayload() == null ? null : new HashMap<>(s.getPayload()));
		clone.setWeight(s.getWeight());
		return clone;
	}

	CacheStats getCacheStats() {
		if (cacheStats == null || lastCacheStatsUpdate + 60_000 < System.currentTimeMillis()) {
			cacheStats = firstLetterCache.stats();
			lastCacheStatsUpdate = System.currentTimeMillis();
		}
		return cacheStats;
	}

	@Override
	public void instrument(Optional<MeterRegistryAdapter> metricsRegistryAdapter, Iterable<Tag> tags) {
		metricsRegistryAdapter.ifPresent(adapter -> addSensors(adapter.getMetricsRegistry(), tags));
	}

	private void addSensors(MeterRegistry reg, Iterable<Tag> tags) {
		reg.gauge(Util.APP_NAME + ".suggester.cache.size", tags, this, me -> me.firstLetterCache.size());
		reg.gauge(Util.APP_NAME + ".suggester.cache.evictionCount", tags, this, me -> me.getCacheStats().evictionCount());
		reg.gauge(Util.APP_NAME + ".suggester.cache.hit_rate", tags, this, me -> me.getCacheStats().hitRate());
		reg.gauge(Util.APP_NAME + ".suggester.cache.miss_rate", tags, this, me -> me.getCacheStats().missRate());
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

	@Override
	public long recordCount() {
		return innerQuerySuggester.get().recordCount();
	}
}
