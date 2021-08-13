package de.cxp.ocs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.elasticsearch.client.Request;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cxp.ocs.model.index.Document;

@ExtendWith({ OCSStack.class })
public class ITPartialUpdates {

	private final static String indexName = "partial_update_test";

	@BeforeAll
	public static void prepareData() throws Exception {
		assert new DataIndexer(OCSStack.getImportClient()).indexTestData(indexName);
	}

	@Test
	public void testAdd() throws Exception {
		OCSStack.getImportClient()
				.putDocuments(indexName, false,
						Collections.singletonList(
								new Document().setId("101").set("title", "Add Test")));
		
		OCSStack.getElasticsearchClient().performRequest(new Request("POST", indexName + "/_flush"));
		
		Document fetchedDoc = OCSStack.getSearchClient().getDocument(indexName, "101");
		assertThat(fetchedDoc).isNotNull();
		assertThat(fetchedDoc.data.get("title")).isEqualTo("Add Test");
	}
}
