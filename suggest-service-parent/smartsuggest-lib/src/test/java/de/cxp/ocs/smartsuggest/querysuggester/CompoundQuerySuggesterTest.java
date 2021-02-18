package de.cxp.ocs.smartsuggest.querysuggester;

import static java.util.Collections.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.*;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.smartsuggest.spi.*;
import de.cxp.ocs.smartsuggest.util.FakeSuggestDataProvider;
import de.cxp.ocs.smartsuggest.util.FakeSuggesterFactory;

public class CompoundQuerySuggesterTest {

	private CompoundQuerySuggester underTest;

	@Test
	public void testWithoutDataProviders() {
		underTest = new CompoundQuerySuggester("", emptyList(), new FakeSuggesterFactory());
		assertTrue(underTest.suggest("a").isEmpty());
	}

	@Test
	public void testWithTwoDataProviders() {
		List<SuggestDataProvider> dataProviders = Arrays.asList(
				new FakeSuggestDataProvider().putData("index-a",
						getSuggestData("type1",
								new SuggestRecord("foo", "", emptyMap(), emptySet(), 100))),
				new FakeSuggestDataProvider().putData("index-a",
						getSuggestData("type2",
								new SuggestRecord("fnord", "", emptyMap(), emptySet(), 200))));
		underTest = new CompoundQuerySuggester("index-a", dataProviders, new FakeSuggesterFactory());
		assertTrue(underTest.suggest("f").size() == 2, () -> "expect both terms as result, only got " + underTest.suggest("f"));
	}

	@Test
	public void testWithTaggedData() {
		List<SuggestDataProvider> dataProviders = Arrays.asList(
				new FakeSuggestDataProvider().putData("index-a",
						getSuggestData("type1",
								new SuggestRecord("foo", "", emptyMap(), singleton("x"), 200))),
				new FakeSuggestDataProvider().putData("index-a",
						getSuggestData("type2",
								new SuggestRecord("fnord", "", emptyMap(), singleton("y"), 100))));
		underTest = new CompoundQuerySuggester("index-a", dataProviders, new FakeSuggesterFactory());
		assertEquals("fnord", underTest.suggest("f", 10, singleton("y")).get(0).getLabel());
	}

	private SuggestData getSuggestData(String type, SuggestRecord... records) {
		return new SuggestData(type, Locale.ROOT, emptySet(), Arrays.asList(records), System.currentTimeMillis());
	}
}
