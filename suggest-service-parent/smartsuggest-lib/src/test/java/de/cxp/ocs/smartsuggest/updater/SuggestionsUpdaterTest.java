package de.cxp.ocs.smartsuggest.updater;

import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggesterProxy;
import de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneSuggesterFactory;
import de.cxp.ocs.smartsuggest.spi.IndexArchiveProvider;
import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import de.cxp.ocs.smartsuggest.spi.standard.DefaultSuggestConfigProvider;
import de.cxp.ocs.smartsuggest.updater.SuggestionsUpdater.SuggestionsUpdaterBuilder;
import de.cxp.ocs.smartsuggest.util.TestDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
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

	private TestDataProvider          testDataProvider;
	private QuerySuggesterProxy       suggesterProxy;
	private SuggestionsUpdaterBuilder updaterBuilder;

	@BeforeEach
	public void basicSetup(@TempDir Path indexBaseDir) {
		testDataProvider = new TestDataProvider();
		var suggestData = SuggestData.builder().type("keywords").suggestRecords(testRecords).modificationTime(System.currentTimeMillis()).build();
		testDataProvider.putData(INDEX_NAME, suggestData);
		suggesterProxy = new QuerySuggesterProxy(INDEX_NAME);
		LuceneSuggesterFactory factory = new LuceneSuggesterFactory(indexBaseDir);
		updaterBuilder = SuggestionsUpdater.builder()
				.dataSourceProvider(testDataProvider)
				.configProvider(new DefaultSuggestConfigProvider())
				.indexName(INDEX_NAME)
				.querySuggesterProxy(suggesterProxy)
				.factory(factory);
	}

	@Test
	public void testStandardDataUpdate() throws Exception {
		SuggestionsUpdater underTest = updaterBuilder.build();

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

	@Test
	public void testArchivedDataUpdate() throws Exception {
		IndexArchiveProvider archiveProvider = new LocalIndexArchiveProvider();
		assert !archiveProvider.hasData(INDEX_NAME);

		{
			SuggestionsUpdater underTest = updaterBuilder.archiveProvider(archiveProvider).build();
			underTest.run();
			assertEquals(3, suggesterProxy.suggest("sea", 5, NO_TAGS).size());
			suggesterProxy.destroy();
		}
		assert archiveProvider.hasData(INDEX_NAME);

		{
			// build updater with new proxy that just can fetch data from the archive
			suggesterProxy = new QuerySuggesterProxy(INDEX_NAME);
			SuggestionsUpdater underTest = updaterBuilder.querySuggesterProxy(suggesterProxy).dataSourceProvider(null).build();
			underTest.run();
			assertEquals(3, suggesterProxy.suggest("sea", 5, NO_TAGS).size());
		}
	}

	@Test
	public void testArchiveIsUpdatedOnNewData() throws Exception {
		IndexArchiveProvider archiveProvider = new LocalIndexArchiveProvider();
		// we have two updater here, one that will index the data from the source and upload to archive
		// and the other one just waiting for archives to get available to fetch them.
		try (
				QuerySuggesterProxy i_suggester = new QuerySuggesterProxy(INDEX_NAME);
				QuerySuggesterProxy f_suggester = new QuerySuggesterProxy(INDEX_NAME)
		) {
			SuggestionsUpdater indexer = updaterBuilder.dataSourceProvider(testDataProvider).archiveProvider(archiveProvider).querySuggesterProxy(i_suggester).build();

			SuggestionsUpdater fetcher = updaterBuilder.dataSourceProvider(null).archiveProvider(archiveProvider)
					.querySuggesterProxy(f_suggester)
					.factory(new LuceneSuggesterFactory(Files.createTempDirectory("fetcher"))).build();

			indexer.run();
			assert archiveProvider.hasData(INDEX_NAME);
			assert i_suggester.isReady();
			assertEquals(3, i_suggester.suggest("sea", 5, NO_TAGS).size());

			assert !f_suggester.isReady();
			fetcher.run();
			assert f_suggester.isReady();
			assertEquals(3, f_suggester.suggest("sea", 5, NO_TAGS).size());

			// now let's update the data through the indexer
			SuggestData testData2 = testDataProvider.loadData(INDEX_NAME).toBuilder()
					.sharpenedQueries(Map.of("sea", List.of("see more")))
					.modificationTime(System.currentTimeMillis())
					.build();
			testDataProvider.putData(INDEX_NAME, testData2);
			indexer.run();
			assertEquals(4, i_suggester.suggest("sea", 5, NO_TAGS).size(), "indexer-suggester should have been updated");

			assertEquals(3, f_suggester.suggest("sea", 5, NO_TAGS).size(), "fetcher-suggester should not have been updated");
			assertEquals(testData2.getModificationTime(), archiveProvider.getLastDataModTime(INDEX_NAME), "archive provider should have been updated");
			fetcher.run();
			assertEquals(4, f_suggester.suggest("sea", 5, NO_TAGS).size(), "fetcher-suggester should have been updated");
		}
	}
}
