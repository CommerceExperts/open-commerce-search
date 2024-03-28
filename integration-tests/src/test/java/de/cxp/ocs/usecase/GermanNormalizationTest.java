package de.cxp.ocs.usecase;

import static de.cxp.ocs.OCSStack.getImportClient;
import static de.cxp.ocs.OCSStack.getSearchClient;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cxp.ocs.OCSStack;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.result.SearchResultSlice;
import de.cxp.ocs.util.DataIndexer;

@ExtendWith({ OCSStack.class })
public class GermanNormalizationTest {

	@Test
	public void test_SS_and_ß_are_handled_equaly() throws Exception {
		DataIndexer dataIndexer = new DataIndexer(getImportClient());
		dataIndexer.setLangcode("de");
		String indexName = "test_ss";

		List<Document> documents = new ArrayList<>();
		documents.add(new Document("1").set("title", "fußballschuhe"));
		documents.add(new Document("2").set("title", "fussballschuhe"));

		assertEquals(documents.size(), dataIndexer.indexTestData(indexName, documents.iterator()));

		ResultHit firstResultFirstHit;
		ResultHit secondResultFirstHit;
		{
			SearchResult result = getSearchClient().search(indexName, new SearchQuery().setQ("fußballschuhe"), null);
			assertEquals(2L, result.slices.get(0).matchCount, () -> idsToString(result.slices.get(0)));
			firstResultFirstHit = result.slices.get(0).getHits().get(0);
		}
		{
			SearchResult result = getSearchClient().search(indexName, new SearchQuery().setQ("fussballschuhe"), null);
			assertEquals(2L, result.slices.get(0).matchCount, () -> idsToString(result.slices.get(0)));
			secondResultFirstHit = result.slices.get(0).getHits().get(0);
		}

		// expect both first hits to have same score
		assertEquals(firstResultFirstHit.getMetaData().get("score"), secondResultFirstHit.getMetaData().get("score"));
	}

	private static String idsToString(SearchResultSlice slice) {
		return slice.getHits().stream().map(ResultHit::getDocument).map(Document::getId).collect(Collectors.joining(", "));
	}
}
