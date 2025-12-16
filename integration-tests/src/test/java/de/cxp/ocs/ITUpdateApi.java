package de.cxp.ocs;

import static de.cxp.ocs.OCSStack.getElasticsearchClient;
import static de.cxp.ocs.OCSStack.getImportClient;
import static de.cxp.ocs.OCSStack.getSearchClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cxp.ocs.api.indexer.UpdateIndexService.Result;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.SearchResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ExtendWith({ OCSStack.class })
public class ITUpdateApi {

	private final static String indexName = "create_via_update_test";

	// no indexation, since we want to test if the update API creates the necessary index
	@AfterEach
	public void deleteIndexes() {
		Request deleteRequest = new Request("DELETE", "ocs-1-" + indexName + "-en");
		try {
			Response response = getElasticsearchClient().performRequest(deleteRequest);
			log.info("deleting index {} responded with {}", indexName, response);
		}
		catch (IOException e) {
			log.error("failed to delete index {} after test: {}:{}", indexName, e.getClass(), e.getMessage());
		}
	}

	@Test
	public void testAddProductWithVariants() throws Exception {
		Map<String, Result> results = getImportClient()
				// put with locale to allow creation
				.putProducts(indexName, false, "en", Collections.singletonList(
						(Product) new Product("005")
								.setVariants(new Document[] {
										new Document().setId("005_0").set("price", 21.5) })
								.set("title", "Add Index With Variants")));
		assertEquals(Result.CREATED, results.get("005"));

		flushIndex();

		Document fetchedDoc = getSearchClient().getDocument(indexName, "005");
		assertThat(fetchedDoc).isNotNull();
		assertThat(fetchedDoc.data.get("title")).isEqualTo("Add Index With Variants");

		SearchResult result = doSimpleSearch("variants");
		assertEquals(1L, result.slices.get(0).matchCount);
	}

	@Test
	public void testAddDocument() throws Exception {
		Map<String, Result> results = getImportClient()
				// put with locale to allow creation
				.putDocuments(indexName, false, "en", Collections.singletonList(
						new Document().setId("101").set("title", "Create Index Test")));

		assertEquals(Result.CREATED, results.get("101"));

		flushIndex();

		Document fetchedDoc = getSearchClient().getDocument(indexName, "101");
		assertThat(fetchedDoc).isNotNull();
		assertThat(fetchedDoc.data.get("title")).isEqualTo("Create Index Test");

		SearchResult result = doSimpleSearch("create");
		assertEquals(1L, result.slices.get(0).matchCount);
	}

	private SearchResult doSimpleSearch(String query) {
		try {
			return getSearchClient().search(indexName, new SearchQuery().setQ(query), null);
		}
		catch (Exception e) {
			log.error("", e);
			return null;
		}
	}

	private void flushIndex() throws IOException {
		getElasticsearchClient().performRequest(new Request("POST", indexName + "/_flush?wait_if_ongoing=true"));
	}

}
