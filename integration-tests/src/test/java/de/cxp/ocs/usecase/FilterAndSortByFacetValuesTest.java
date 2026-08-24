package de.cxp.ocs.usecase;

import static de.cxp.ocs.OCSStack.getImportClient;
import static de.cxp.ocs.OCSStack.getSearchClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.elasticsearch.core.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cxp.ocs.OCSStack;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.*;
import de.cxp.ocs.util.DataIndexer;

@ExtendWith({ OCSStack.class })
public class FilterAndSortByFacetValuesTest {

	static final String indexName = "test_campaign_indexing";

	@BeforeAll
	public static void indexData() throws Exception {
		// we can use the same index for all our arranged-search tests
		DataIndexer dataIndexer = new DataIndexer(getImportClient());

		/* Given 4 documents and 2 campaigns that covers part of the documents in a strict oder:
		 * campaign c1: p2, p3, p1
		 * campaign c2: p2, p1 */
		List<Document> documents = List.of(
				new Document("p1").set("title", "product 1").set("price", "12.99").addAttribute(new Attribute("campaign", "1", "3")).addAttribute(new Attribute("campaign", "2", "20")),
				new Document("p2").set("title", "product 2").set("price", "22.99").addAttribute(new Attribute("campaign", "1", "1")).addAttribute(new Attribute("campaign", "2", "10")),
				new Document("p3").set("title", "product 3").set("price", "32.99").addAttribute(new Attribute("campaign", "1", "2")),
				new Document("p4").set("title", "product 4").set("price", "42.99"));
		assertEquals(documents.size(), dataIndexer.indexTestData(indexName, documents.iterator()));
	}

	@Test
	public void testSortedCampaigns() throws Exception {
		{
			SearchResult result_c1 = getSearchClient().search(indexName, new SearchQuery().setSort("campaign.id.1"), Map.of("campaign.id", "1"));
			List<ResultHit> hits_c1 = result_c1.slices.get(0).hits;
			assertEquals(3, hits_c1.size());
			assertEquals("p2", hits_c1.get(0).document.id);
			assertEquals("p3", hits_c1.get(1).document.id);
			assertEquals("p1", hits_c1.get(2).document.id);
		}

		{
			SearchResult result_c2 = getSearchClient().search(indexName, new SearchQuery().setSort("campaign.id.2"), Map.of("campaign.id", "2"));
			List<ResultHit> hits_c2 = result_c2.slices.get(0).hits;
			assertEquals(2, hits_c2.size());
			assertEquals("p2", hits_c2.get(0).document.id);
			assertEquals("p1", hits_c2.get(1).document.id);
		}
	}

	@Test
	public void testFilteredRangeFacet() throws Exception {
		{
			SearchResult result_c1 = getSearchClient().search(indexName, new SearchQuery(), Map.of("campaign.id", "1"));
			List<Facet> facets = result_c1.slices.get(0).getFacets();
			Facet campaignFacet = facets.stream().filter(f -> "campaign".equals(f.getFieldName())).findFirst().orElseThrow();
			assertEquals(1, campaignFacet.getEntries().size());

			FacetEntry entry1 = campaignFacet.getEntries().get(0);
			assertTrue(entry1 instanceof RangeFacetEntry, "instead: " + entry1.getClass().getCanonicalName());
			assertEquals(1.0, ((RangeFacetEntry) entry1).getLowerBound());
			assertEquals(3.0, ((RangeFacetEntry) entry1).getUpperBound());

			Facet priceFacet = facets.stream().filter(f -> "price".equals(f.getFieldName())).findFirst().orElseThrow();
			FacetEntry priceEntry = priceFacet.getEntries().get(0);
			assertTrue(priceEntry instanceof RangeFacetEntry, "instead: " + priceEntry.getClass().getCanonicalName());
			assertEquals(12.99, ((RangeFacetEntry) priceEntry).getLowerBound());
			assertEquals(32.99, ((RangeFacetEntry) priceEntry).getUpperBound());
		}

		{
			SearchResult result_c2 = getSearchClient().search(indexName, new SearchQuery(), Map.of("campaign.id", "2"));
			List<Facet> facets = result_c2.slices.get(0).getFacets();
			Facet campaignFacet = facets.stream().filter(f -> "campaign".equals(f.getFieldName())).findFirst().orElseThrow();
			assertEquals(1, campaignFacet.getEntries().size());

			FacetEntry entry1 = campaignFacet.getEntries().get(0);
			assertTrue(entry1 instanceof RangeFacetEntry, "instead: " + entry1.getClass().getCanonicalName());

			assertEquals(10.0, ((RangeFacetEntry) entry1).getLowerBound());
			assertEquals(20.0, ((RangeFacetEntry) entry1).getUpperBound());
		}
	}

	/* Due to aggSampling=2, we expect only the first two products being used for facet creation, even though all 3
	 * products should be in the result */
	@Disabled("agg sampling ignores sorting, so without a query and scores it's unpredictable which documents are used for aggregation")
	@Test
	public void testFilteredRangeFacetWithAggSampling() throws Exception {
		{
			SearchResult result_c1 = getSearchClient().search(indexName, new SearchQuery().setSort("campaign.id.1"), Map.of("campaign.id", "1", "aggSampling", "2"));
			List<ResultHit> hits_c1 = result_c1.slices.get(0).hits;
			assertEquals(3, hits_c1.size());

			List<Facet> facets = result_c1.slices.get(0).getFacets();

			Facet campaignFacet = facets.stream().filter(f -> "campaign".equals(f.getFieldName())).findFirst().orElseThrow();
			assertEquals(1, campaignFacet.getEntries().size());
			FacetEntry entry1 = campaignFacet.getEntries().get(0);
			assertTrue(entry1 instanceof RangeFacetEntry, "instead: " + entry1.getClass().getCanonicalName());
			assertEquals(1.0, ((RangeFacetEntry) entry1).getLowerBound());
			assertEquals(2.0, ((RangeFacetEntry) entry1).getUpperBound());

			Facet priceFacet = facets.stream().filter(f -> "price".equals(f.getFieldName())).findFirst().orElseThrow();
			FacetEntry priceEntry = priceFacet.getEntries().get(0);
			assertTrue(priceEntry instanceof RangeFacetEntry, "instead: " + priceEntry.getClass().getCanonicalName());
			assertEquals(22.99, ((RangeFacetEntry) priceEntry).getLowerBound());
			assertEquals(32.99, ((RangeFacetEntry) priceEntry).getUpperBound());
		}
	}

	@Test
	public void testFilteredRangeFacetForTwoRangeFilters() throws Exception {
		{
			SearchResult result_c1 = getSearchClient().search(indexName, new SearchQuery(), Map.of("campaign.id", "1", "price", "10-25"));

			List<ResultHit> hits_c1 = result_c1.slices.get(0).hits;
			assertEquals(2, hits_c1.size());

			List<Facet> facets = result_c1.slices.get(0).getFacets();

			Facet campaignFacet = facets.stream().filter(f -> "campaign".equals(f.getFieldName())).findFirst().orElseThrow();
			assertEquals(1, campaignFacet.getEntries().size());
			FacetEntry entry1 = campaignFacet.getEntries().get(0);
			assertTrue(entry1 instanceof RangeFacetEntry, "instead: " + entry1.getClass().getCanonicalName());

			// both products in result have value 1 and 3 for campaign.id=1, so expect full range of those values
			assertEquals(1.0, ((RangeFacetEntry) entry1).getLowerBound());
			assertEquals(3.0, ((RangeFacetEntry) entry1).getUpperBound());

			Facet priceFacet = facets.stream().filter(f -> "price".equals(f.getFieldName())).findFirst().orElseThrow();
			FacetEntry priceEntry = priceFacet.getEntries().get(0);
			assertTrue(priceEntry instanceof RangeFacetEntry, "instead: " + priceEntry.getClass().getCanonicalName());

			// expect total range, since price is not filter-sensitive
			assertEquals(12.99, ((RangeFacetEntry) priceEntry).getLowerBound());
			assertEquals(32.99, ((RangeFacetEntry) priceEntry).getUpperBound());
		}

		{
			// use different price filter here, so we can assert, that documents with campaign.id=2 are filtered
			SearchResult result_c2 = getSearchClient().search(indexName, new SearchQuery(), Map.of("campaign.id", "2", "price", "15-25"));
			List<ResultHit> hits_c2 = result_c2.slices.get(0).hits;
			assertEquals(1, hits_c2.size());

			List<Facet> facets = result_c2.slices.get(0).getFacets();
			Optional<Facet> campaignFacet = facets.stream().filter(f -> "campaign".equals(f.getFieldName())).findFirst();
			// single document matches, so there is no campaign facet expected anymore
			assertFalse(campaignFacet.isPresent());

			Facet priceFacet = facets.stream().filter(f -> "price".equals(f.getFieldName())).findFirst().orElseThrow();
			FacetEntry priceEntry = priceFacet.getEntries().get(0);
			assertTrue(priceEntry instanceof RangeFacetEntry, "instead: " + priceEntry.getClass().getCanonicalName());

			// expect total range, since price is not filter-sensitive
			assertEquals(12.99, ((RangeFacetEntry) priceEntry).getLowerBound());
			assertEquals(22.99, ((RangeFacetEntry) priceEntry).getUpperBound());
		}
	}
}
