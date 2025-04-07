package de.cxp.ocs.smartsuggest.querysuggester.lucene;

import de.cxp.ocs.smartsuggest.querysuggester.modified.ModifiedTermsService;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static de.cxp.ocs.smartsuggest.querysuggester.lucene.TestSetupUtil.*;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;

public class LuceneQuerySuggesterPersistenceTest {

	@Test
	public void testStoreAndRecovery(@TempDir Path indexFolder) throws Exception {
		SuggestConfig suggestConfig = new SuggestConfig();
		suggestConfig.setAlwaysDoFuzzy(true);
		suggestConfig.setLocale(Locale.GERMAN);

		try(var underTest = new LuceneQuerySuggester(indexFolder, suggestConfig, mock(ModifiedTermsService.class), getWordSet(Locale.ROOT))) {
			List<SuggestRecord> toIndex = new ArrayList<>(asList(
					asSuggestRecord("search a", "label a", 200),
					asSuggestRecord("search b", "label b", 190),
					asSuggestRecord("filtered search c", "label c", 180, Set.of("any_tag"))));
			underTest.index(toIndex).get();

			assert underTest.isReady();
			assertAllFunctionsWork(underTest);

			underTest.commit();
		}

		try(var underTest = new LuceneQuerySuggester(indexFolder, suggestConfig, mock(ModifiedTermsService.class), getWordSet(Locale.ROOT))) {
			assert underTest.isReady();
			assertAllFunctionsWork(underTest);
		}
	}

	private static void assertAllFunctionsWork(LuceneQuerySuggester underTest) {
		assert !underTest.suggest("sea").isEmpty() : "exact prefix search should return results";
		assert !underTest.suggest("lab").isEmpty() : "prefix search on labels should return results";
		assert !underTest.suggest("sea", 10, Set.of("any_tag")).isEmpty() : "filtered search should return results";
		assert !underTest.suggest("labl").isEmpty() : "fuzzy search should return results";
	}
}
