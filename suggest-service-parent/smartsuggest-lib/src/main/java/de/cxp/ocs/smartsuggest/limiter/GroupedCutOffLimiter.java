package de.cxp.ocs.smartsuggest.limiter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import de.cxp.ocs.smartsuggest.querysuggester.Suggestion;
import de.cxp.ocs.smartsuggest.spi.CommonPayloadFields;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * <p>
 * This limiter groups the result by the specified payload entry and limits each
 * group to the specified size. If the final list is longer than the requested
 * limit, the least important group is truncated accordingly.
 * </p>
 * <p>
 * If the final list is shorter as the requested limit, nothing is done, so it
 * will stay short! Only exception: if there are suggestions from a group that
 * was not configured, they are appended to the final list, but not more than
 * specified by the "defaultLimit".
 * </p>
 * <p>
 * With the optional given "groupDeduplicationOrder", the suggestions will be
 * deduplicated. The array defines preferred groups. Suggestions of the groups
 * defined first will be preferred over suggestions from other groups.
 * </p>
 */

@RequiredArgsConstructor
public class GroupedCutOffLimiter implements Limiter {

	public final static String OTHER_SHARE_KEY = CommonPayloadFields.PAYLOAD_TYPE_OTHER;

	@NonNull
	private final String groupingKey;

	private final int defaultLimit;

	@NonNull
	private final LinkedHashMap<String, Integer> limitConf;

	private final String[] groupDeduplicationOrder;

	@Override
	public List<Suggestion> limit(List<Suggestion> suggestions, int limit) {
		Map<String, List<Suggestion>> groupedSuggestions = suggestions.stream().collect(Collectors.groupingBy(this::groupKey));
		if (groupDeduplicationOrder != null && groupDeduplicationOrder.length > 0) {
			SuggestDeduplicator.deduplicate(groupedSuggestions, groupDeduplicationOrder);
		}

		List<Suggestion> finalList = new ArrayList<>(Math.min(limit, suggestions.size()));
		for (Entry<String, Integer> limitEntry : limitConf.entrySet()) {
			List<Suggestion> groupedList = groupedSuggestions.remove(limitEntry.getKey());
			if (groupedList != null) {
				int groupLimit = Math.min(groupedList.size(), limitEntry.getValue());
				finalList.addAll(groupedList.subList(0, groupLimit));
			}
		}

		Iterator<List<Suggestion>> otherSuggestionsIterator = groupedSuggestions.values().iterator();
		while (finalList.size() < limit && otherSuggestionsIterator.hasNext()) {
			List<Suggestion> groupedList = otherSuggestionsIterator.next();
			// get min of: "remaining space", "groupListSize" and "defaultLimit"
			int groupLimit = Math.min(limit - finalList.size(), groupedList.size());
			groupLimit = Math.min(defaultLimit, groupLimit);
			finalList.addAll(groupedList.subList(0, groupLimit));
		}
		if (finalList.size() > limit) {
			finalList = finalList.subList(0, limit);
		}
		return finalList;
	}

	private String groupKey(Suggestion suggestion) {
		return suggestion.getPayload() == null ? OTHER_SHARE_KEY : suggestion.getPayload().getOrDefault(groupingKey, OTHER_SHARE_KEY);
	}

}
