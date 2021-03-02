package de.cxp.ocs.smartsuggest.limiter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.cxp.ocs.smartsuggest.querysuggester.Suggestion;

public class GroupedCutOffLimiterTest {

	private GroupedCutOffLimiter underTest;

	@BeforeEach
	public void setup() {
		LinkedHashMap<String, Integer> limitConf = new LinkedHashMap<>();
		limitConf.put("keyword", 5);
		limitConf.put("brand", 3);
		limitConf.put("category", 4);
		underTest = new GroupedCutOffLimiter("type", 3, limitConf);
	}

	@Test
	public void standardUseCase() {
		List<Suggestion> bigList = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			bigList.add(new Suggestion("b_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "brand")));
			bigList.add(new Suggestion("c_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "category")));
			bigList.add(new Suggestion("k_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "keyword")));
		}

		List<Suggestion> limited12 = underTest.limit(bigList, 12);
		assertEquals(12, limited12.size());
		assertEquals("k_1", limited12.get(0).getLabel());
		assertEquals("b_1", limited12.get(5).getLabel());
		assertEquals("c_1", limited12.get(8).getLabel());

		List<Suggestion> limited15 = underTest.limit(bigList, 15);
		// we still expect only 12 results, because the configured limits of the
		// available groups only sum up to 12
		assertEquals(12, limited15.size());
		assertEquals("k_1", limited15.get(0).getLabel());
		assertEquals("b_1", limited15.get(5).getLabel());
		assertEquals("c_1", limited15.get(8).getLabel());

		// still the assertions look similar, there are now just less category
		// suggestions
		List<Suggestion> limited10 = underTest.limit(bigList, 10);
		assertEquals(10, limited10.size());
		assertEquals("k_1", limited10.get(0).getLabel());
		assertEquals("b_1", limited10.get(5).getLabel());
		assertEquals("c_1", limited10.get(8).getLabel());
	}

	@Test
	public void oneGroupNotInResult() {
		List<Suggestion> bigList = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			bigList.add(new Suggestion("c_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "category")));
			bigList.add(new Suggestion("k_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "keyword")));
			// no brands part of result
		}

		List<Suggestion> limited12 = underTest.limit(bigList, 12);
		assertEquals(9, limited12.size());
		assertEquals("k_1", limited12.get(0).getLabel());
		assertEquals("c_1", limited12.get(5).getLabel());
	}

	@Test
	public void withUnconfiguredGroupsInResult() {
		List<Suggestion> bigList = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			bigList.add(new Suggestion("c_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "category")));
			bigList.add(new Suggestion("k_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "keyword")));
			bigList.add(new Suggestion("b_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "brand")));
			bigList.add(new Suggestion("f_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "foo")));
			bigList.add(new Suggestion("x_" + String.valueOf(i + 1)));
		}

		// the configured limits are enough to fill the list
		List<Suggestion> limited12 = underTest.limit(bigList, 12);
		assertEquals(12, limited12.size());
		assertEquals("k_1", limited12.get(0).getLabel());
		assertEquals("b_1", limited12.get(5).getLabel());
		assertEquals("c_1", limited12.get(8).getLabel());
		assertEquals("c_4", limited12.get(11).getLabel());

		List<Suggestion> limited20 = underTest.limit(bigList, 20);
		// the configured limits + the default limit for two groups sum up to 18
		assertEquals(18, limited20.size());
		assertEquals("k_1", limited20.get(0).getLabel());
		assertEquals("b_1", limited20.get(5).getLabel());
		assertEquals("c_1", limited20.get(8).getLabel());
		assertEquals("c_4", limited20.get(11).getLabel());
		// it's undefined if "x" or "f" comes first -> it depends on the
		// hashcode of their group-name
		assertEquals("x_1", limited20.get(12).getLabel());
		assertEquals("f_1", limited20.get(15).getLabel());

		List<Suggestion> limited15 = underTest.limit(bigList, 15);
		assertEquals(15, limited15.size());
		assertEquals("k_1", limited15.get(0).getLabel());
		assertEquals("b_1", limited15.get(5).getLabel());
		assertEquals("c_1", limited15.get(8).getLabel());
		assertEquals("c_4", limited15.get(11).getLabel());
		// it's undefined if "x" or "f" comes first -> it depends on the
		// hashcode of their group-name
		assertEquals("x_1", limited15.get(12).getLabel());
	}

	@Test
	public void onlyConfigreDefaultLimit() {
		underTest = new GroupedCutOffLimiter("type", 3, new LinkedHashMap<>());
		List<Suggestion> bigList = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			bigList.add(new Suggestion("c_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "category")));
			bigList.add(new Suggestion("k_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "keyword")));
			bigList.add(new Suggestion("b_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "brand")));
			bigList.add(new Suggestion("x_" + String.valueOf(i + 1)));
		}

		List<Suggestion> limited12 = underTest.limit(bigList, 12);
		assertEquals(12, limited12.size());
		assertTrue(limited12.get(0).getLabel().endsWith("_1"));
		assertTrue(limited12.get(3).getLabel().endsWith("_1"));
		assertTrue(limited12.get(6).getLabel().endsWith("_1"));
		assertTrue(limited12.get(9).getLabel().endsWith("_1"));

		List<Suggestion> limited10 = underTest.limit(bigList, 10);
		assertEquals(10, limited10.size());
		assertTrue(limited10.get(0).getLabel().endsWith("_1"));
		assertTrue(limited10.get(3).getLabel().endsWith("_1"));
		assertTrue(limited10.get(6).getLabel().endsWith("_1"));
		assertTrue(limited10.get(9).getLabel().endsWith("_1"));

		List<Suggestion> limited20 = underTest.limit(bigList, 20);
		assertEquals(12, limited20.size());
		assertTrue(limited20.get(0).getLabel().endsWith("_1"));
		assertTrue(limited20.get(3).getLabel().endsWith("_1"));
		assertTrue(limited20.get(6).getLabel().endsWith("_1"));
		assertTrue(limited20.get(9).getLabel().endsWith("_1"));
	}

	@Test
	public void emptyResult() {
		List<Suggestion> limitedList = underTest.limit(Collections.emptyList(), 5);
		assertTrue(limitedList.isEmpty());
	}
}
