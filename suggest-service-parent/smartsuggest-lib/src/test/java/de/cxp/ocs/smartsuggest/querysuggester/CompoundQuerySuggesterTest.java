package de.cxp.ocs.smartsuggest.querysuggester;

import static java.util.Collections.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.smartsuggest.limiter.Limiter;
import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestDataProvider;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import de.cxp.ocs.smartsuggest.util.FakeSuggestDataProvider;
import de.cxp.ocs.smartsuggest.util.FakeSuggesterFactory;

public class CompoundQuerySuggesterTest {

	private CompoundQuerySuggester underTest;

	private static Limiter limiter = (list, limit) -> list;

	@Test
	public void testWithoutDataProviders() throws IOException {
		underTest = new CompoundQuerySuggester("", emptyList(), new FakeSuggesterFactory(), limiter);
		assertTrue(underTest.suggest("a").isEmpty());
	}

	@Test
	public void testWithTwoDataProviders() throws IOException {
		List<SuggestDataProvider> dataProviders = Arrays.asList(
				new FakeSuggestDataProvider().putData("index-a",
						getSuggestData("type1",
								new SuggestRecord("foo", "", emptyMap(), emptySet(), 100))),
				new FakeSuggestDataProvider().putData("index-a",
						getSuggestData("type2",
								new SuggestRecord("fnord", "", emptyMap(), emptySet(), 200))));
		underTest = new CompoundQuerySuggester("index-a", dataProviders, new FakeSuggesterFactory(), limiter);
		assertTrue(underTest.suggest("f").size() == 2, () -> "expect both terms as result, only got " + underTest.suggest("f"));
	}

	@Test
	public void testWithTaggedData() throws IOException {
		List<SuggestDataProvider> dataProviders = Arrays.asList(
				new FakeSuggestDataProvider().putData("index-a",
						getSuggestData("type1",
								new SuggestRecord("foo", "", emptyMap(), singleton("x"), 200))),
				new FakeSuggestDataProvider().putData("index-a",
						getSuggestData("type2",
								new SuggestRecord("fnord", "", emptyMap(), singleton("y"), 100))));
		underTest = new CompoundQuerySuggester("index-a", dataProviders, new FakeSuggesterFactory(), limiter);
		assertEquals("fnord", underTest.suggest("f", 10, singleton("y")).get(0).getLabel());
	}

	private SuggestData getSuggestData(String type, SuggestRecord... records) {
		return new SuggestData(type, Locale.ROOT, emptySet(), Arrays.asList(records), System.currentTimeMillis());
	}
}
