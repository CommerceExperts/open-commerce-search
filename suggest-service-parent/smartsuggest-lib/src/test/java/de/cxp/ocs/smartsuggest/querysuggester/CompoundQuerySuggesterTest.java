package de.cxp.ocs.smartsuggest.querysuggester;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.smartsuggest.spi.SuggestConfigProvider;
import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestDataProvider;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import de.cxp.ocs.smartsuggest.spi.standard.DefaultSuggestConfigProvider;
import de.cxp.ocs.smartsuggest.util.TestDataProvider;
import de.cxp.ocs.smartsuggest.util.FakeSuggesterFactory;

public class CompoundQuerySuggesterTest {

	private CompoundQuerySuggester underTest;

	private static final SuggestConfigProvider	configProvider	= new DefaultSuggestConfigProvider();

	@Test
	public void testWithoutDataProviders() throws IOException {
		underTest = new CompoundQuerySuggester("", emptyList(), configProvider, new FakeSuggesterFactory());
		assertTrue(underTest.suggest("a").isEmpty());
	}

	@Test
	public void testWithTwoDataProviders() throws IOException {
		List<SuggestDataProvider> dataProviders = Arrays.asList(
				new TestDataProvider().putData("index-a",
						getSuggestData("type1",
								new SuggestRecord("foo", "", emptyMap(), emptySet(), 100))),
				new TestDataProvider().putData("index-a",
						getSuggestData("type2",
								new SuggestRecord("fnord", "", emptyMap(), emptySet(), 200))));
		underTest = new CompoundQuerySuggester("index-a", dataProviders, configProvider, new FakeSuggesterFactory());
		assertEquals(2, underTest.suggest("f").size(), () -> "expect both terms as result, only got " + underTest.suggest("f"));
	}

	@Test
	public void testWithTaggedData() throws IOException {
		List<SuggestDataProvider> dataProviders = Arrays.asList(
				new TestDataProvider().putData("index-a",
						getSuggestData("type1",
								new SuggestRecord("foo", "", emptyMap(), singleton("x"), 200))),
				new TestDataProvider().putData("index-a",
						getSuggestData("type2",
								new SuggestRecord("fnord", "", emptyMap(), singleton("y"), 100))));
		underTest = new CompoundQuerySuggester("index-a", dataProviders, configProvider, new FakeSuggesterFactory());
		assertEquals("fnord", underTest.suggest("f", 10, singleton("y")).getFirst().getLabel());
	}

	private SuggestData getSuggestData(String type, SuggestRecord... records) {
		return SuggestData.builder().type(type)
				.suggestRecords(Arrays.asList(records))
				.modificationTime(System.currentTimeMillis())
				.build();
	}
}
