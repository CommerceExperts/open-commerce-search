package de.cxp.ocs.smartsuggest.limiter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.smartsuggest.querysuggester.Suggestion;

public class SuggestDeduplicatorTest {

	@Test
	public void withoutPriorityOrder() {
		Map<String, List<Suggestion>> groupedSuggestions = new LinkedHashMap<>();
		groupedSuggestions.put("group1", listOf(s("s1"), s("s2"), s("s3")));
		groupedSuggestions.put("group2", listOf(s("s4"), s("s2"), s("s5")));
		SuggestDeduplicator.deduplicate(groupedSuggestions, new String[0]);

		assertEquals(3, groupedSuggestions.get("group1").size());
		assertEquals(2, groupedSuggestions.get("group2").size());
	}

	@Test
	public void withSinglePriorityGroup() {
		Map<String, List<Suggestion>> groupedSuggestions = new LinkedHashMap<>();
		groupedSuggestions.put("group1", listOf(s("s1"), s("s2"), s("s3")));
		groupedSuggestions.put("group2", listOf(s("s4"), s("s2"), s("s5")));
		SuggestDeduplicator.deduplicate(groupedSuggestions, new String[] { "group2" });

		assertEquals(2, groupedSuggestions.get("group1").size());
		assertEquals(3, groupedSuggestions.get("group2").size());
	}

	@Test
	public void withTwoPriorityGroups() {
		Map<String, List<Suggestion>> groupedSuggestions = new LinkedHashMap<>();
		groupedSuggestions.put("group1", listOf(s("s1"), s("s2"), s("s3")));
		groupedSuggestions.put("group2", listOf(s("s4"), s("s2"), s("s5")));
		groupedSuggestions.put("group3", listOf(s("s4"), s("s6"), s("s3")));
		SuggestDeduplicator.deduplicate(groupedSuggestions, new String[] { "group2", "group3" });

		assertEquals(1, groupedSuggestions.get("group1").size());
		assertEquals(3, groupedSuggestions.get("group2").size());
		assertEquals(2, groupedSuggestions.get("group3").size());
	}

	@Test
	public void emptyLists() {
		Map<String, List<Suggestion>> map = new HashMap<>();
		SuggestDeduplicator.deduplicate(map, new String[] { "group2" });
		assertTrue(map.isEmpty());

		map.put("g1", new ArrayList<>());
		assertTrue(map.get("g1").isEmpty());
	}

	private Suggestion s(String label) {
		return new Suggestion(label);
	}

	private List<Suggestion> listOf(Suggestion... suggestions) {
		// we need a mutable list
		return new ArrayList<>(Arrays.asList(suggestions));
	}
}

