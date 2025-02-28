package de.cxp.ocs.usecase;

import de.cxp.ocs.OCSStack;
import de.cxp.ocs.client.SearchClient;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.result.SearchResultSlice;
import de.cxp.ocs.util.DataIndexer;
import de.cxp.ocs.model.index.Category;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.cxp.ocs.OCSStack.getImportClient;
import static de.cxp.ocs.OCSStack.getSearchClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith({ OCSStack.class })
public class MultipleCategoryTreesTest {

	static String indexName = "multi_category";

	@SuppressWarnings("deprecation")
	@BeforeAll
	public static void indexData() throws Exception {
		DataIndexer dataIndexer = new DataIndexer(getImportClient());

		Category[] catPath1_L2 = new Category[] { new Category("c_1a", "Alpha One"), new Category("c_1b", "Beta One") };
		Category[] catPath2_L3 = new Category[] { new Category("c_2a", "Alpha Two"), new Category("c_2b", "Beta Two"),
				new Category("c_2g", "Gamma") }; // last level has same name in both trees
		Category[] catPath3_L2 = new Category[] { new Category("c_3a", "Alpha Three"), new Category("c_2b", "Beta Three") };
		Category[] catPath3_L3 = new Category[] { new Category("c_3a", "Alpha Three"), new Category("c_2b", "Beta Three"), new Category("c_2g", "Gamma") };

		Category[] taxonomyPath1 = new Category[] { new Category("t1.1", "Home and Garden"), new Category("t1.2", "Kitchen") };
		Category[] taxonomyPath2 = new Category[] { new Category("t2.1", "Sports & Health"), new Category("t2.9", "Apparel") };
		Category[] taxonomyPath3 = new Category[] { new Category("t3.1", "Apparel & Fashion"), new Category("t3.2", "Shirts + Sweaters") };

		List<Document> documents = List.of(
				// only cat path
				new Document("d1").set("title", "product 1").set("price", "12.99").addCategory(catPath1_L2),
				new Document("d2").set("title", "product 2").set("price", "22.99").addPath("categories", catPath2_L3), //combine new approach with deprecated legacy method
				// two products have both paths
				new Document("d3").set("title", "product 3").set("price", "32.99").addCategory(catPath3_L2).addPath("taxonomy", taxonomyPath1),
				new Document("d4").set("title", "product 4").set("price", "42.99").addCategory(catPath3_L3).addPath("taxonomy", taxonomyPath2),
				// only taxonomy path
				new Document("d5").set("title", "product 5").set("price", "52.99").addPath("taxonomy", taxonomyPath3));
		assertEquals(documents.size(), dataIndexer.indexTestData(indexName, documents.iterator()));
	}

	@Test
	public void testBothCategoryTreesRetrieved() throws Exception {
		SearchClient searchClient = getSearchClient();
		SearchQuery params = new SearchQuery().setWithFacets(true);

		SearchResult result = searchClient.search(indexName, params, Collections.emptyMap());
		assertEquals(1, result.getSlices().size());
		SearchResultSlice searchResultSlice = result.getSlices().getFirst();

		facetExists(searchResultSlice, "categories", 3);
		facetExists(searchResultSlice, "taxonomy", 3);
	}

	@Test
	public void testFilterOnTaxonomy() throws Exception {
		SearchClient searchClient = getSearchClient();
		SearchResult result = searchClient.search(indexName, new SearchQuery(), Map.of("taxonomy", "Home and Garden"));

		assertEquals(1, result.getSlices().size());
		SearchResultSlice searchResultSlice = result.getSlices().getFirst();
		assertEquals(1, searchResultSlice.getMatchCount());
		assertEquals(1, searchResultSlice.hits.size());

		ResultHit firstHit = searchResultSlice.hits.getFirst();
		assertEquals("product 3", firstHit.getDocument().getData().get("title"));
	}

	@Test
	public void testFilterOnCategory() throws Exception {
		SearchClient searchClient = getSearchClient();
		SearchResult result = searchClient.search(indexName, new SearchQuery(), Map.of("categories", "Alpha Three"));

		assertEquals(1, result.getSlices().size());
		SearchResultSlice searchResultSlice = result.getSlices().getFirst();
		assertEquals(2, searchResultSlice.getMatchCount());
		assertEquals(2, searchResultSlice.hits.size());

		ResultHit resultHit = searchResultSlice.hits.getFirst();
		assertEquals("product 3", resultHit.getDocument().getData().get("title"));

		ResultHit lastHit = searchResultSlice.hits.getLast();
		assertEquals("product 4", lastHit.getDocument().getData().get("title"));

		facetExists(searchResultSlice, "taxonomy", 2);
	}

	private static void facetExists(SearchResultSlice searchResultSlice, String taxonomy, int expectedEntries) {
		Optional<Facet> taxonomyFacet = searchResultSlice.facets.stream().filter(facet -> taxonomy.equals(facet.getFieldName())).findFirst();
		assertTrue(taxonomyFacet.isPresent());
		assertEquals(expectedEntries, taxonomyFacet.get().entries.size());
	}
}
