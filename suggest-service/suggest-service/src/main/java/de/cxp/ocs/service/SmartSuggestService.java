package de.cxp.ocs.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import de.cxp.ocs.smartsuggest.QuerySuggestManager;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester.Result;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SmartSuggestService {

	private final QuerySuggestManager querySuggestManager;
	
	private final LoadingCache<String, Boolean> deletionCache = CacheBuilder.newBuilder()
			.expireAfterAccess(Integer.getInteger("suggester_max_idle_minutes", 30), TimeUnit.MINUTES)
			.removalListener(new RemovalListener<String, Boolean>() {
				@Override
				public void onRemoval(RemovalNotification<String, Boolean> notification) {
					log.info("shutting down suggester for indexName {}", notification.getKey());
					querySuggestManager.destroyQuerySuggester(notification.getKey());
				}})
			.build(new CacheLoader<String, Boolean>() {
				@Override
				public Boolean load(String key) throws Exception {
					return Boolean.TRUE;
				}
			}); 

	public SmartSuggestService(QuerySuggestManager querySuggestManager) {
		this.querySuggestManager = querySuggestManager;
	}

	public List<QuerySuggester.Result> getSuggestions(final String query, final String indexName, int maxResults) {
		boolean synchronous = Boolean.getBoolean(QuerySuggestManager.DEBUG_PROPERTY);
		// notify deletion cache about access
		deletionCache.getUnchecked(indexName);
		QuerySuggester qm = querySuggestManager.getQuerySuggester(indexName, synchronous);

		List<QuerySuggester.Result> suggestions = qm.suggest(query, maxResults, Collections.emptySet());
		log.debug("Suggestions '{}' -> '{}' for indexName '{}'", query, suggestions, indexName);
		return suggestions;
	}

	public List<String> getSimpleSuggestions(String query, String indexName, int maxSuggestions) {
		List<Result> groupedSuggestions = this.getSuggestions(query, indexName, maxSuggestions);
		if (groupedSuggestions.isEmpty()) return Collections.emptyList();
		
		List<String> simpleSuggestions = new ArrayList<>(maxSuggestions);

		for (Result suggestGroup : groupedSuggestions) {
			if ((simpleSuggestions.size() + suggestGroup.getSuggestions().size()) > maxSuggestions) {
				for (String s : suggestGroup.getSuggestions()) {
					simpleSuggestions.add(s);
					if (simpleSuggestions.size() == maxSuggestions) break;
				}
			}
			else {
				simpleSuggestions.addAll(suggestGroup.getSuggestions());
			}

			if (simpleSuggestions.size() == maxSuggestions) break;
		}

		return simpleSuggestions;
	}

	@PreDestroy
	public void shutdownHook() {
		querySuggestManager.close();
	}
}
