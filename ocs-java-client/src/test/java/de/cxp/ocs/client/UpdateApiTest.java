package de.cxp.ocs.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.cxp.ocs.api.indexer.UpdateIndexService.Result;
import de.cxp.ocs.model.index.Document;

@TestMethodOrder(OrderAnnotation.class)
public class UpdateApiTest {

	@BeforeAll
	public static void setup() {
		System.out.println("starting up docker compose");
	}

	@Test
	@Order(1)
	public void bulkIndex() {
		System.out.println("indexing...");
	}

	@Test
	public void updateBrand() {
		System.out.println("updating brand...");
		ImportClient underTest = new ImportClient("http://localhost:8535");
		Map<String, Result> patchDocuments = underTest.patchDocuments("mind_sk_sk", Arrays.asList(
				new Document().setId("100310461").set("brand", "blub")));

		assertEquals(Result.UPDATED, patchDocuments.get("100310461"));
		System.out.println(patchDocuments);
	}

	@Test
	public void updatePrice() {
		System.out.println("updating price..");
	}
}
