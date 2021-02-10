package de.cxp.ocs;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.cache.*;

import de.cxp.ocs.api.SuggestService;
import de.cxp.ocs.model.suggest.Suggestion;
import de.cxp.ocs.smartsuggest.QuerySuggestManager;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
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
	public List<Suggestion> suggest(String indexName, String userQuery, Integer limit, String filter) throws Exception {
		deletionCache.getUnchecked(indexName);
		QuerySuggester qm = querySuggestManager.getQuerySuggester(indexName, false);

		Set<String> tagsFilter;
		if (filter != null && !filter.isEmpty()) {
			tagsFilter = new HashSet<>(Arrays.asList(filter.split(",")));
		}
		else {
			tagsFilter = Collections.emptySet();
		}

		return qm.suggest(userQuery, limit, tagsFilter)
				.stream()
				// map internal "Suggestion"
				// (de.cxp.ocs.smartsuggest.querysuggester.Suggestion)
				// to external "Suggestion"
				// (de.cxp.ocs.model.suggest.Suggestion.Suggestion)
				.map(suggestion -> {
					return new Suggestion(suggestion.getLabel())
							.setPayload(suggestion.getPayload());
				})
				.collect(Collectors.toList());
	}

}
