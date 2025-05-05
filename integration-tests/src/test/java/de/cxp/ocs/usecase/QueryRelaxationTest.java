package de.cxp.ocs.usecase;

import static de.cxp.ocs.OCSStack.getImportClient;
import static de.cxp.ocs.OCSStack.getSearchClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cxp.ocs.OCSStack;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.util.DataIndexer;

@ExtendWith({ OCSStack.class })
public class QueryRelaxationTest {

	@Test
	public void testPartiallyKnownTermCombinationAreSearchedInclusively() throws Exception {
		/* Given a query with 3 or more terms, where no document matches all of them
		 * but there are different documents that hit at 2 different term combination,
		 * we expect all of them to be found, but not documents that only match a single term. */
		String query = "makita saw blade";

		DataIndexer dataIndexer = new DataIndexer(getImportClient());
		String indexName = "test_partial_matches";

		List<Document> documents = new ArrayList<>();
		// no product matches all 3 terms
		// but those 3 should be found, because each of them matches 2 terms
		documents.add(new Document("1").set("title", "makita electric circular saw").set("brand", "makita")); // no "blade"
		documents.add(new Document("2").set("title", "netherite saw blade").set("brand", "mojang")); // no "makita"
		documents.add(new Document("3").set("title", "makita diamond blade").set("brand", "makita")); // no "saw"
		// those should not be found:
		documents.add(new Document("4").set("title", "hard iron blade").set("brand", "noname"));
		documents.add(new Document("5").set("title", "makita drill").set("brand", "makita"));
		documents.add(new Document("6").set("title", "bosch sabre saw").set("brand", "bosch"));

		assertEquals(documents.size(), dataIndexer.indexTestData(indexName, documents.iterator()));
		{
			SearchResult result = getSearchClient().search(indexName, new SearchQuery().setQ(query), null);
			assertEquals(3L, result.getSlices().get(0).getMatchCount());

			// first two matches should be with "brand=makita" due to the extra match in the brand field
			assertThat(result.getSlices().get(0).getHits().get(0).document.id).isIn("1", "3");
			assertThat(result.getSlices().get(0).getHits().get(1).document.id).isIn("1", "3");
			// ensure last document is not one of the undesired ones
			assertThat(result.getSlices().get(0).getHits().get(2).document.id).isEqualTo("2");
		}
		{
			SearchResult resultFiltered = getSearchClient().search(indexName, new SearchQuery().setQ(query), Map.of("brand", "makita"));
			assertEquals(2L, resultFiltered.getSlices().get(0).getMatchCount());
		}
	}
}
