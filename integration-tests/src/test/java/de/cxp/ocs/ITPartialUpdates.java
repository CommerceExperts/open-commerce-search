package de.cxp.ocs;

import static de.cxp.ocs.OCSStack.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Collections;

import org.elasticsearch.client.Request;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;

@ExtendWith({ OCSStack.class })
public class ITPartialUpdates {

	private final static String indexName = "partial_update_test";

	@BeforeAll
	public static void prepareData() throws Exception {
		assert new DataIndexer(getImportClient()).indexTestData(indexName);
	}

	@Test
	public void testAdd() throws Exception {
		getImportClient()
				.putDocuments(indexName, false, Collections.singletonList(
								new Document().setId("101").set("title", "Add Test")));
		
		flushIndex();
		
		Document fetchedDoc = getSearchClient().getDocument(indexName, "101");
		assertThat(fetchedDoc).isNotNull();
		assertThat(fetchedDoc.data.get("title")).isEqualTo("Add Test");
	}

	@Test
	public void testPatchVariantPrice() throws Exception {
		getImportClient()
				.patchDocuments(indexName, Collections.singletonList(
						new Product("005").setVariants(new Document[] { new Document().setId("005_2").set("price", 21.5) })));
		flushIndex();

		Document patchedDocument = getSearchClient().getDocument(indexName, "005");

		assertThat(patchedDocument).isInstanceOf(Product.class);
		Product patchedProduct = ((Product) patchedDocument);

		assertThat(patchedProduct.variants).hasSize(2);
		Document patchedVariant = patchedProduct.variants[0].getId().equals("005_0") ? patchedProduct.variants[0] : patchedProduct.variants[1];

		assertThat(patchedVariant.data.get("price")).isEqualTo(21.5);
	}

	private void flushIndex() throws IOException {
		getElasticsearchClient().performRequest(new Request("POST", indexName + "/_flush"));
	}

}
