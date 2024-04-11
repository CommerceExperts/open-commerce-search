package de.cxp.ocs.usecase;

import static de.cxp.ocs.OCSStack.getImportClient;
import static de.cxp.ocs.OCSStack.getSearchClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.elasticsearch.core.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cxp.ocs.OCSStack;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.*;
import de.cxp.ocs.util.DataIndexer;

@ExtendWith({ OCSStack.class })
public class FilterAndSortByFacetValues {

	static String indexName = "test_campaign_indexing";

	@BeforeAll
	public static void indexData() throws Exception {
		// we can use the same index for all our arranged-search tests
		DataIndexer dataIndexer = new DataIndexer(getImportClient());

		/* Given 4 documents and 2 campaigns that covers part of the documents in a strict oder:
		 * campaign c1: p2, p3, p1
		 * campaign c2: p2, p1 */
		List<Document> documents = List.of(
				new Document("p1").set("title", "product 1").addAttribute(new Attribute("campaign", "1", "3")).addAttribute(new Attribute("campaign", "2", "20")),
				new Document("p2").set("title", "product 2").addAttribute(new Attribute("campaign", "1", "1")).addAttribute(new Attribute("campaign", "2", "10")),
				new Document("p3").set("title", "product 3").addAttribute(new Attribute("campaign", "1", "2")),
				new Document("p4").set("title", "product 4"));
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
}
