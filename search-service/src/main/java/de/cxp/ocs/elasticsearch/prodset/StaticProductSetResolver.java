package de.cxp.ocs.elasticsearch.prodset;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.StaticProductSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StaticProductSetResolver implements ProductSetResolver {

	private final static String FN_PREFIX = "field:";

	@Override
	public StaticProductSet resolve(final ProductSet productSet, Set<String> excludedIds, Searcher searcher, SearchContext searchContext) {
		StaticProductSet staticSet = (StaticProductSet) productSet;

		int expectedHitCount = staticSet.getSize();
		Set<String> requestedIds = null;
		QueryBuilder requestIdsQuery;
		// search in a field
		if (staticSet.name != null && staticSet.name.startsWith(FN_PREFIX)) {
			String searchFieldName = staticSet.name.substring(FN_PREFIX.length());
			requestIdsQuery = searchContext.fieldConfigIndex.getField(searchFieldName)
					.map(searchField -> buildNumberSearchQuery(searchField, staticSet.getIds()))
					.orElseThrow(() -> new IllegalArgumentException("StaticProductSetResolver: requested number field '" + searchFieldName + "' not searchable"));
			// since we are searching a non-unique field, we can expect more hits than IDs searched
			expectedHitCount = 1000;
			// requestedIds stays null
		}

		// these ids are actual unique document IDs
		else {
			if (excludedIds != null && !excludedIds.isEmpty()) {
				Set<String> filteredIds = new HashSet<String>(staticSet.getIds().length);
				for (String id : staticSet.getIds()) {
					if (!excludedIds.contains(id)) {
						filteredIds.add(id);
					}
				}
				requestIdsQuery = QueryBuilders.idsQuery().addIds(filteredIds.toArray(new String[filteredIds.size()]));
			}
			else {
				requestIdsQuery = QueryBuilders.idsQuery().addIds(staticSet.getIds());
			}
			requestedIds = ((IdsQueryBuilder) requestIdsQuery).ids();
			expectedHitCount = requestedIds.size();
		}

		try {
			SearchResponse searchResponse = searcher.executeSearchRequest(SearchSourceBuilder.searchSource()
					.query(requestIdsQuery)
					.fetchSource(false)
					.size(expectedHitCount));
			if (searchResponse.getHits().getTotalHits().value == 0) {
				staticSet.setIds(new String[0]);
			}
			else if (requestedIds == null) {
				String[] foundIds = StreamSupport.stream(searchResponse.getHits().spliterator(), false)
						.map(SearchHit::getId)
						.toArray(String[]::new);
				staticSet.setIds(foundIds);
			}
			else if (searchResponse.getHits().getTotalHits().value < expectedHitCount && searchResponse.getHits().getTotalHits().relation.equals(TotalHits.Relation.EQUAL_TO)) {
				Set<String> foundIds = StreamSupport.stream(searchResponse.getHits().spliterator(), false)
						.map(SearchHit::getId)
						.collect(Collectors.toSet());
				// some ids are invalid are not part of response. remove them from set but keep order
				staticSet.setIds(getFilteredInOrder(staticSet.getIds(), foundIds));
			}
			else if (expectedHitCount < staticSet.getSize()) {
				// some ids were deduplicated with the request, remove them from set but keep order
				staticSet.setIds(getFilteredInOrder(staticSet.getIds(), requestedIds));
			}
		}
		catch (Exception e) {
			log.error("{}: {} while verifying productSet ids. Won't verify.", e.getClass().getCanonicalName(), e.getMessage());
		}
		return staticSet;
	}

	private QueryBuilder buildNumberSearchQuery(Field searchField, String[] searchNumbers) {
		BoolQueryBuilder combinedQuery = QueryBuilders.boolQuery();
		int boost = searchNumbers.length + 1;
		for (String number : searchNumbers) {
			combinedQuery.should(
					QueryBuilders.termQuery(FieldConstants.SEARCH_DATA + "." + searchField.getName(), number)
							.boost(boost));
			boost--;
		}
		return combinedQuery;
	}

	private String[] getFilteredInOrder(String[] orderedIds, Set<String> includeIds) {
		String[] filteredIds = new String[includeIds.size()];
		int insertIndex = 0;
		Set<String> checklist = new HashSet<>(includeIds);
		for (int i = 0; i < orderedIds.length; i++) {
			if (checklist.remove(orderedIds[i])) {
				filteredIds[insertIndex++] = orderedIds[i];
			}
		}
		if (insertIndex < filteredIds.length) {
			// do not use insertIndex+1 - it is already incremented with the last insertion
			log.debug("filtered ids contains less elements ({}) than expected ({}). Seems like some ids were returned that were not requested originaly", filteredIds.length, insertIndex);
			filteredIds = Arrays.copyOf(filteredIds, insertIndex);
		}
		return filteredIds;
	}

	@Override
	public boolean runAsync() {
		return true;
	}

}
