package de.cxp.ocs.smartsuggest.limiter;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.cxp.ocs.smartsuggest.querysuggester.Suggestion;

/**
 * Utility class to deduplicate grouped suggestions.
 */
final class SuggestDeduplicator {

	static void deduplicate(Map<String, List<Suggestion>> groupedSuggestions, String[] groupDeduplicationOrder) {
		Set<String> seenSuggestions = new HashSet<>();
		Set<String> remainingGroups = new LinkedHashSet<>(groupedSuggestions.keySet());
		for (String preferedGroup : groupDeduplicationOrder) {
			List<Suggestion> list = groupedSuggestions.get(preferedGroup);
			if (list == null) continue;
			removeSeen(seenSuggestions, list);
			remainingGroups.remove(preferedGroup);
		}
		for (String remainingGroup : remainingGroups) {
			List<Suggestion> list = groupedSuggestions.get(remainingGroup);
			removeSeen(seenSuggestions, list);
		}
	}

	private static void removeSeen(Set<String> seenSuggestions, List<Suggestion> list) {
		Iterator<Suggestion> iterator = list.iterator();
		while (iterator.hasNext()) {
			if (!seenSuggestions.add(iterator.next().getLabel().trim().toLowerCase())) {
				iterator.remove();
			}
		}
	}
}
