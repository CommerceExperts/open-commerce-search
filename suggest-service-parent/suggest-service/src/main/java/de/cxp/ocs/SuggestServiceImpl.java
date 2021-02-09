package de.cxp.ocs;

import java.util.*;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.*;

import de.cxp.ocs.api.SuggestService;
import de.cxp.ocs.model.suggest.Suggestion;
import de.cxp.ocs.smartsuggest.QuerySuggestManager;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SuggestServiceImpl implements SuggestService {

	private final QuerySuggestManager querySuggestManager;

	private final LoadingCache<String, Boolean> deletionCache = CacheBuilder.newBuilder()
			.expireAfterAccess(Integer.getInteger("suggester_max_idle_minutes", 30), TimeUnit.MINUTES)
			.removalListener(new RemovalListener<String, Boolean>() {

				@Override
				public void onRemoval(RemovalNotification<String, Boolean> notification) {
					log.info("shutting down suggester for index {}", notification.getKey());
					querySuggestManager.destroyQuerySuggester(notification.getKey());
				}
			})
			.build(new CacheLoader<String, Boolean>() {

				@Override
				public Boolean load(String key) throws Exception {
					return Boolean.TRUE;
				}
			});

	@Override
	public List<Suggestion> suggest(String indexName, String userQuery, Integer limit, Map<String, String> filters) throws Exception {
		deletionCache.getUnchecked(indexName);
		QuerySuggester qm = querySuggestManager.getQuerySuggester(indexName, false);
		List<QuerySuggester.Result> groupedSuggestions = qm.suggest(userQuery, limit, Collections.emptySet());

		if (groupedSuggestions.isEmpty()) return Collections.emptyList();

		List<Suggestion> suggestionResult = new ArrayList<>(limit);

		for (Result suggestGroup : groupedSuggestions) {
			for (String phrase : suggestGroup.getSuggestions()) {
				suggestionResult.add(new Suggestion(phrase));
				if (suggestionResult.size() == limit) break;
			}

			if (suggestionResult.size() == limit) break;
		}

		return suggestionResult;
	}

}
