package de.cxp.ocs.usecase;

import static de.cxp.ocs.OCSStack.getImportClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cxp.ocs.OCSStack;
import de.cxp.ocs.client.SearchClient;
import de.cxp.ocs.model.params.ArrangedSearchQuery;
import de.cxp.ocs.model.params.DynamicProductSet;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.StaticProductSet;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.result.SearchResultSlice;
import de.cxp.ocs.util.DataIndexer;

@ExtendWith({ OCSStack.class })
public class HeroProductsTest {

	static String indexName = "test_hero_products";

	@BeforeAll
	public static void setup() throws Exception {
		// we can use the same index for all our arranged-search tests
		DataIndexer dataIndexer = new DataIndexer(getImportClient());
		assertTrue(dataIndexer.indexTestData(indexName));
	}

	@Test
	public void testDynamicArrangedProducts() throws Exception {
		SearchClient searchClient = OCSStack.getSearchClient();
		ArrangedSearchQuery arrangedSearchQuery = (ArrangedSearchQuery) new ArrangedSearchQuery()
				.setArrangedProductSets(new ProductSet[] {
						new DynamicProductSet().setName("x").setQuery("barfoo")
								.setSort("price")
								// although there are 2 "brand=Barfoo" products in the index, the limit should be
								// considered
								.setLimit(1)
				})
				.setQ("helmet");
		SearchResult result = searchClient.arrangedSearch(indexName, arrangedSearchQuery);
		SearchResultSlice mainSlice = result.getSlices().get(0);
		assertEquals(2, mainSlice.matchCount);
		{
			// arranged search should inject products on top
			ResultHit hit0 = mainSlice.getHits().get(0);
			assertEquals("Barfoo", hit0.getDocument().data.get("brand"));
			assertEquals("003", hit0.getDocument().id);
		}
		{
			// regular query should be found
			ResultHit hit1 = mainSlice.getHits().get(1);
			assertEquals("Helmut", hit1.getDocument().data.get("brand"));
		}
	}

	@Test
	public void testStandardStaticArrangedProducts() throws Exception {
		SearchClient searchClient = OCSStack.getSearchClient();
		ArrangedSearchQuery arrangedSearchQuery = (ArrangedSearchQuery) new ArrangedSearchQuery()
				.setArrangedProductSets(new ProductSet[] {
						new StaticProductSet(new String[] { "005", "003", "00x", "006", "003" }, "stuff")
				})
				.setQ("helmet");
		SearchResult result = searchClient.arrangedSearch(indexName, arrangedSearchQuery);
		SearchResultSlice mainSlice = result.getSlices().get(0);
		assertEquals(4, mainSlice.matchCount);
		// arranged search should inject products in specified order
		assertEquals("005", mainSlice.getHits().get(0).getDocument().id);
		assertEquals("003", mainSlice.getHits().get(1).getDocument().id);
		// 00x does not exist and should simply be ignored
		assertEquals("006", mainSlice.getHits().get(2).getDocument().id);
		// last hit from natural result
		assertEquals("Helmut", mainSlice.getHits().get(3).getDocument().data.get("brand"));
	}

	@Test
	public void testStaticArrangedProductsAsSeparateSlice() throws Exception {
		SearchClient searchClient = OCSStack.getSearchClient();
		StaticProductSet staticProductSet = new StaticProductSet(new String[] { "005", "005" }, "stuff");
		staticProductSet.setAsSeparateSlice(true);
		ArrangedSearchQuery arrangedSearchQuery = (ArrangedSearchQuery) new ArrangedSearchQuery()
				.setArrangedProductSets(new ProductSet[] { staticProductSet })
				.setQ("helmet");
		SearchResult result = searchClient.arrangedSearch(indexName, arrangedSearchQuery);
		assertEquals(2, result.getSlices().size());

		SearchResultSlice heroSlice = result.getSlices().get(0);
		assertEquals("stuff", heroSlice.label);
		assertEquals(1, heroSlice.matchCount);
		assertEquals("005", heroSlice.getHits().get(0).getDocument().id);

		SearchResultSlice mainSlice = result.getSlices().get(1);
		assertEquals("002", mainSlice.getHits().get(0).getDocument().id);

		assertThat(mainSlice.getFacets())
				.anyMatch(facet -> facet.getFieldName().equals("brand"))
				.flatExtracting(Facet::getEntries)
				// assert the facet value of the injected product is there
				.anyMatch(entry -> entry.getKey().equals("Fluffy Unicorn"));
	}

	@Test
	public void testStaticArrangedProductsOnDifferentField() throws Exception {
		SearchClient searchClient = OCSStack.getSearchClient();
		ArrangedSearchQuery arrangedSearchQuery = (ArrangedSearchQuery) new ArrangedSearchQuery()
				.setArrangedProductSets(new ProductSet[] {
						new StaticProductSet(new String[] { "fu19", "av30", "xx00", "fu19" }, "field:artNr")
				})
				.setQ("helmet");
		SearchResult result = searchClient.arrangedSearch(indexName, arrangedSearchQuery);

		SearchResultSlice mainSlice = result.getSlices().get(0);
		assertEquals(3, mainSlice.matchCount);
		assertEquals("005", mainSlice.getHits().get(0).getDocument().id);
		assertEquals("004", mainSlice.getHits().get(1).getDocument().id);
		assertEquals("002", mainSlice.getHits().get(2).getDocument().id);
	}

	@Test
	public void testStaticProductSetDoesNotMatchAnyExistingDocument() throws Exception {
		SearchClient searchClient = OCSStack.getSearchClient();
		ArrangedSearchQuery arrangedSearchQuery = (ArrangedSearchQuery) new ArrangedSearchQuery()
				.setArrangedProductSets(new ProductSet[] {
						new StaticProductSet(new String[] { "x0x", "x00" }, "off")
				})
				.setQ("helmet");
		SearchResult result = searchClient.arrangedSearch(indexName, arrangedSearchQuery);
		SearchResultSlice mainSlice = result.getSlices().get(0);
		assertEquals(1, mainSlice.matchCount);
		assertEquals("002", mainSlice.getHits().get(0).getDocument().id);
	}
}
