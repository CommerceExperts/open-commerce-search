package de.cxp.ocs.smartsuggest.updater;

import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggesterProxy;
import de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneSuggesterFactory;
import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import de.cxp.ocs.smartsuggest.spi.standard.DefaultSuggestConfigProvider;
import de.cxp.ocs.smartsuggest.util.TestDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.cxp.ocs.smartsuggest.util.TestSetupUtil.asSuggestRecord;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SuggestionsUpdaterTest {

	private final static String      INDEX_NAME = "test-updater";
	private final static Set<String> NO_TAGS    = Set.of();

	private static final List<SuggestRecord> testRecords = asList(
			asSuggestRecord("search a", "label a", 200),
			asSuggestRecord("search b", "label b", 190),
			asSuggestRecord("filtered search c", "label c", 180, Set.of("any_tag")));

	private TestDataProvider testDataProvider;
	private QuerySuggesterProxy suggesterProxy;
	private SuggestionsUpdater underTest;

	@BeforeEach
	public void basicSetup(@TempDir Path indexBaseDir) {
		testDataProvider = new TestDataProvider();
		testDataProvider.putData(INDEX_NAME, SuggestData.builder().type("keywords").suggestRecords(testRecords).modificationTime(System.currentTimeMillis()).build());
		suggesterProxy = new QuerySuggesterProxy(INDEX_NAME, TestDataProvider.class.getSimpleName());
		LuceneSuggesterFactory factory = new LuceneSuggesterFactory(indexBaseDir);
		underTest = new SuggestionsUpdater(testDataProvider, new DefaultSuggestConfigProvider(), null, INDEX_NAME, suggesterProxy, factory);
	}

	@Test
	public void testStandardDataUpdate() throws Exception {
		assert !suggesterProxy.isReady();
		underTest.run();
		assert suggesterProxy.isReady();
		assertEquals(3, suggesterProxy.suggest("sea", 5, NO_TAGS).size());

		SuggestData testData2 = testDataProvider.loadData(INDEX_NAME).toBuilder()
				.sharpenedQueries(Map.of("sea", List.of("see more")))
				.modificationTime(System.currentTimeMillis())
				.build();
		testDataProvider.putData(INDEX_NAME, testData2);

		// as long as the updater did not run, there should be no change
		assertEquals(3, suggesterProxy.suggest("sea", 5, NO_TAGS).size());
		underTest.run();
		// more suggestions after update.run
		assertEquals(4, suggesterProxy.suggest("sea", 5, NO_TAGS).size());

		suggesterProxy.destroy();
	}

}
