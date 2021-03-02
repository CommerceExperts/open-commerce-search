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

public class ConfigurableShareLimiterTest {

	private ConfigurableShareLimiter underTest;

	@BeforeEach
	public void setup() {
		LinkedHashMap<String, Double> shareConfiguration = new LinkedHashMap<>();
		shareConfiguration.put("keyword", 0.3);
		shareConfiguration.put("brand", 0.2);
		shareConfiguration.put("category", 0.5);
		underTest = new ConfigurableShareLimiter("type", shareConfiguration);
	}

	@Test
	public void standardUseCase() {
		List<Suggestion> bigList = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			bigList.add(new Suggestion("b_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "brand")));
			bigList.add(new Suggestion("c_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "category")));
			bigList.add(new Suggestion("k_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "keyword")));
		}

		List<Suggestion> list10 = underTest.limit(bigList, 10);
		assertEquals(10, list10.size());
		assertEquals("k_3", list10.get(2).getLabel());
		assertEquals("b_1", list10.get(3).getLabel());
		assertEquals("b_2", list10.get(4).getLabel());
		assertEquals("c_1", list10.get(5).getLabel());
		assertEquals("c_5", list10.get(9).getLabel());

		// the weight of category is so high, it gets 6 slots in result
		List<Suggestion> list11 = underTest.limit(bigList, 11);
		assertEquals(11, list11.size());
		assertEquals("k_3", list11.get(2).getLabel());
		assertEquals("b_1", list11.get(3).getLabel());
		assertEquals("b_2", list11.get(4).getLabel());
		assertEquals("c_1", list11.get(5).getLabel());
		assertEquals("c_6", list11.get(10).getLabel());

		// to correct down, "category" suggestions are removed
		List<Suggestion> list9 = underTest.limit(bigList, 9);
		assertEquals(9, list9.size());
		assertEquals("k_3", list9.get(2).getLabel());
		assertEquals("b_1", list9.get(3).getLabel());
		assertEquals("b_2", list9.get(4).getLabel());
		assertEquals("c_1", list9.get(5).getLabel());
		assertEquals("c_4", list9.get(8).getLabel());
	}

	@Test
	public void oneGroupNotInResult() {
		List<Suggestion> bigList = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			bigList.add(new Suggestion("b_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "brand")));
			bigList.add(new Suggestion("k_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "keyword")));
			// no categories part of result
		}

		List<Suggestion> list10 = underTest.limit(bigList, 10);
		assertEquals(10, list10.size());
		assertEquals("k_6", list10.get(5).getLabel());
		assertEquals("b_1", list10.get(6).getLabel());
		assertEquals("b_4", list10.get(9).getLabel());

		// to correct up, "keywords" suggestions are added
		List<Suggestion> list11 = underTest.limit(bigList, 11);
		assertEquals(11, list11.size());
		assertEquals("k_7", list11.get(6).getLabel());
		assertEquals("b_1", list11.get(7).getLabel());
		assertEquals("b_4", list11.get(10).getLabel());

		// to correct down, "brand" suggestions are removed
		List<Suggestion> list9 = underTest.limit(bigList, 9);
		assertEquals(9, list9.size());
		assertEquals("k_6", list10.get(5).getLabel());
		assertEquals("b_1", list10.get(6).getLabel());
		assertEquals("b_3", list10.get(8).getLabel());
	}

	@Test
	public void onlyOneGroupInResult() {
		List<Suggestion> bigList = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			bigList.add(new Suggestion("b_" + String.valueOf(i + 1)).setPayload(Collections.singletonMap("type", "brand")));
			// no category and keywords
		}
		List<Suggestion> sameLimitList = underTest.limit(bigList, bigList.size());
		assertEquals(bigList.size(), sameLimitList.size());

		List<Suggestion> limitedList = underTest.limit(bigList, 5);
		assertEquals(5, limitedList.size());
		assertEquals("b_1", limitedList.get(0).getLabel());
		assertEquals("b_5", limitedList.get(4).getLabel());
	}

	@Test
	public void emptyResult() {
		List<Suggestion> limitedList = underTest.limit(Collections.emptyList(), 5);
		assertTrue(limitedList.isEmpty());
	}
}
