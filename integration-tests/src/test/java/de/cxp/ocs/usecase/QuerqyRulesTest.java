package de.cxp.ocs.usecase;

import static de.cxp.ocs.OCSStack.getImportClient;
import static de.cxp.ocs.OCSStack.getSearchClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cxp.ocs.OCSStack;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.result.SearchResultSlice;
import de.cxp.ocs.util.DataIndexer;

@ExtendWith({ OCSStack.class })
public class QuerqyRulesTest {

	private final static String indexName = "querqy_rules_test";

	@BeforeAll
	public static void prepareData() throws Exception {
		assertTrue(new DataIndexer(getImportClient()).indexTestData(indexName) > 0);
	}

	@Test
	public void testSearchWithBoosting() throws Exception {
		SearchResult searchResult = getSearchClient().search(indexName, new SearchQuery().setQ("bike"), Collections.emptyMap());
		assertThat(searchResult.slices.size()).isEqualTo(1);

		SearchResultSlice mainSlice = searchResult.slices.get(0);
		assertThat(mainSlice.matchCount).isEqualTo(4);

		assertEquals("007", mainSlice.hits.get(0).document.id);
		assertEquals("001", mainSlice.hits.get(1).document.id);

		assertTrue((double) mainSlice.hits.get(0).metaData.get("score") > (double) mainSlice.hits.get(1).metaData.get("score"));
	}
}
