package de.cxp.ocs;

import de.cxp.ocs.model.suggest.Suggestion;
import de.cxp.ocs.util.DataIndexer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static de.cxp.ocs.OCSStack.getImportClient;
import static de.cxp.ocs.OCSStack.getSuggestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@ExtendWith({ OCSStack.class })
public class ITSuggestService {


	private final static String indexName = "suggest_test";

	@BeforeAll
	public static void prepareData() throws Exception {
		assertTrue(new DataIndexer(getImportClient()).indexTestData(indexName) > 0);
	}

	@Test
	public void testBrandSuggest() throws Exception {
		@SuppressWarnings("UnusedAssignment") // request results in advance, so the suggest service can start loading the data in the background
		List<Suggestion> suggestResult = fetchSuggestions("bar");
		long startTS = System.currentTimeMillis();
		long waitTime;
		do {
			//noinspection BusyWait
			Thread.sleep(1000);
			suggestResult = fetchSuggestions("bar");
			waitTime = System.currentTimeMillis() - startTS;
		} while (waitTime < 10_000 && suggestResult.isEmpty());

		log.info("suggest results: " + suggestResult);
		assert !suggestResult.isEmpty() : "no suggestions found";
		assertEquals("Barfoo", suggestResult.getFirst().getPhrase());
	}

	private List<Suggestion> fetchSuggestions(String query) throws Exception {
		return getSuggestClient().suggest(indexName, query, 10, null);
	}
}
