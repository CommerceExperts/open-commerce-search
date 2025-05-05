package de.cxp.ocs.smartsuggest.querysuggester.lucene;

import de.cxp.ocs.smartsuggest.querysuggester.modified.ModifiedTermsService;
import de.cxp.ocs.smartsuggest.spi.IndexArchive;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static de.cxp.ocs.smartsuggest.util.TestSetupUtil.asSuggestRecord;
import static de.cxp.ocs.smartsuggest.util.TestSetupUtil.getWordSet;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;

@Slf4j
public class LuceneQuerySuggesterPersistenceTest {

	private static final List<SuggestRecord> testRecords = asList(
			asSuggestRecord("search a", "label a", 200),
			asSuggestRecord("search b", "label b", 190),
			asSuggestRecord("filtered search c", "label c", 180, Set.of("any_tag")));

	private static void assertAllFunctionsWork(LuceneQuerySuggester underTest) {
		assert !underTest.suggest("sea").isEmpty() : "exact prefix search should return results";
		assert !underTest.suggest("lab").isEmpty() : "prefix search on labels should return results";
		assert !underTest.suggest("sea", 10, Set.of("any_tag")).isEmpty() : "filtered search should return results";
		assert !underTest.suggest("labl").isEmpty() : "fuzzy search should return results";
	}

	private static SuggestConfig minimalSuggestConfig() {
		SuggestConfig suggestConfig = new SuggestConfig();
		suggestConfig.setAlwaysDoFuzzy(true);
		suggestConfig.setLocale(Locale.GERMAN);
		return suggestConfig;
	}

	@Test
	public void testStoreAndRecovery(@TempDir Path indexFolder) throws Exception {
		SuggestConfig suggestConfig = minimalSuggestConfig();

		Long modTime = null;
		try(var underTest = new LuceneQuerySuggester(indexFolder, suggestConfig, mock(ModifiedTermsService.class), getWordSet(Locale.ROOT), null)) {
			underTest.index(testRecords, System.currentTimeMillis()).get();
			assert underTest.isReady();
			assertAllFunctionsWork(underTest);
			underTest.commit();
			modTime = underTest.getIndexModTime().toEpochMilli();
		}

		try(var underTest = new LuceneQuerySuggester(indexFolder, suggestConfig, mock(ModifiedTermsService.class), getWordSet(Locale.ROOT), modTime)) {
			assert underTest.isReady();
			assertAllFunctionsWork(underTest);
		}
	}

	@Test
	public void testStoreAndRecoveryWithFactory(@TempDir Path baseDir) throws Exception {
		LuceneSuggesterFactory factory = new LuceneSuggesterFactory(baseDir);
		SuggestData suggestData = new SuggestData();
		suggestData.setModificationTime(System.currentTimeMillis());
		suggestData.setSuggestRecords(testRecords);
		var config = minimalSuggestConfig();

		IndexArchive archive;
		try (LuceneQuerySuggester underTest = factory.getSuggester(suggestData, config)) {
			assertAllFunctionsWork(underTest);

			archive = factory.createArchive(underTest);
			underTest.destroy();
		}

		log.info("recovering lucene suggester from archive {}", archive);
		try (LuceneQuerySuggester recovered = factory.recover(archive, config)) {
			assertAllFunctionsWork(recovered);
		}

	}
}
