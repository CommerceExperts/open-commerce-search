package de.cxp.ocs.elasticsearch;

import static de.cxp.ocs.config.FieldConstants.NUMBER_FACET_DATA;
import static de.cxp.ocs.config.FieldConstants.PATH_FACET_DATA;
import static de.cxp.ocs.config.FieldConstants.RESULT_DATA;
import static de.cxp.ocs.config.FieldConstants.SCORES;
import static de.cxp.ocs.config.FieldConstants.SEARCH_DATA;
import static de.cxp.ocs.config.FieldConstants.SORT_DATA;
import static de.cxp.ocs.config.FieldConstants.TERM_FACET_DATA;
import static de.cxp.ocs.config.FieldConstants.VARIANTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.cxp.ocs.Application;
import de.cxp.ocs.api.indexer.UpdateIndexService.Result;
import de.cxp.ocs.conf.ApplicationProperties;
import de.cxp.ocs.controller.IndexerCache;
import de.cxp.ocs.controller.UpdateIndexController;
import de.cxp.ocs.indexer.IndexerFactory;
import de.cxp.ocs.indexer.model.*;
import de.cxp.ocs.model.index.Category;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;

@SuppressWarnings({ "unchecked", "deprecation" })
@AutoConfigureMockMvc
@SpringBootTest
@ContextConfiguration(classes = { UpdateIndexController.class, IndexerCache.class, IndexerFactory.class, ElasticsearchCRUDTest.TestConf.class })
public class ElasticsearchCRUDTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RestHighLevelClient esClient;

	@Autowired
	private ObjectMapper objectMapper;

	private static ElasticsearchContainer	container;
	private static int						HTTP_TEST_PORT	= -1;

	@Configuration
	static class TestConf extends Application {

		@Bean
		@Override
		public RestClient getRestClient(ApplicationProperties properties) {
			System.out.println("initializing ES client");
			properties.getConnectionConfiguration().setHosts("127.0.0.1:" + HTTP_TEST_PORT);
			properties.getConnectionConfiguration().setUseCompatibilityMode(true);
			return RestClientBuilderFactory.createRestClientBuilder(properties.getConnectionConfiguration()).build();
		}

	}

	@BeforeAll
	public static void spinUpEs() {
		container = ElasticsearchContainerUtil.spinUpEs("8.19.8");
		HTTP_TEST_PORT = container.getMappedPort(ElasticsearchContainerUtil.ES_PORT);
	}

	@AfterAll
	public static void shutdown() {
		if (container != null && container.isRunning()) container.stop();
	}

	@Test
	public void freshIndexNotExists() throws Exception {
		assertIndexExists("nonexisting_index", false);

		mockMvc.perform(MockMvcRequestBuilders
				.patch("/indexer-api/v1/update/nonexisting")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(MockMvcResultMatchers.status().is(400));

		mockMvc.perform(MockMvcRequestBuilders
				.delete("/indexer-api/v1/update/nonexisting?id=123")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(MockMvcResultMatchers.status().is(404));
	}

	@Test
	public void addDocumentToFreshIndex() throws Exception {
		// requirement for indexes that are created via update API: they must be in the format <name>-<locale>
		String indexName = "first_index";
		String id = "a1";
		Document doc = new Document(id);
		doc.data.put("title", "document a");
		doc.data.put("brand", "foo");

		assertIndexExists(indexName, false);
		putDocument(indexName, doc);
		assertIndexExists(indexName, true);

		IndexableItem indexedDoc = getIndexedDocument(indexName, id);
		assertNotNull(indexedDoc);
		assertEquals("document a", indexedDoc.getResultData().get("title"));

		assertEquals("brand", indexedDoc.getTermFacetData().get(0).getName());
		assertEquals("foo", indexedDoc.getTermFacetData().get(0).getValue());

		assertEquals("document a foo", StringUtils.join((List<String>) indexedDoc.getSearchData().get("search_combi"), ' '));
	}

	@Test
	public void patchCombiFieldAndFacet() throws Exception {
		String indexName = "patch_test";
		String id = "b1";
		Document doc = new Document(id);
		doc.data.put("title", "document b");
		doc.data.put("brand", "the best");
		putDocument(indexName, doc);
		assertIndexExists(indexName, true);

		// new document only with partial data
		Document docPatch = new Document(id);
		docPatch.data.put("brand", "the very best");
		patchDocument(indexName, docPatch);

		IndexableItem indexedDoc = getIndexedDocument(indexName, id);
		assertNotNull(indexedDoc);
		assertEquals("brand", indexedDoc.getTermFacetData().get(0).getName());
		assertEquals("the very best", indexedDoc.getTermFacetData().get(0).getValue());
		assertEquals("document b the very best", joinToString(indexedDoc.getSearchData().get("search_combi"), ' '));
	}

	@Test
	public void patchNumericFacetField() throws Exception {
		String indexName = "patch_test";
		String id = "c1";
		Document doc = new Document(id);
		doc.data.put("title", "document c");
		doc.data.put("price", 12.99);
		putDocument(indexName, doc);
		assertIndexExists(indexName, true);

		// new document only with partial data
		Document docPatch = new Document(id);
		docPatch.data.put("price", 15.10);
		patchDocument(indexName, docPatch);

		IndexableItem indexedDoc = getIndexedDocument(indexName, id);
		assertNotNull(indexedDoc);
		assertEquals("price", indexedDoc.getNumberFacetData().get(0).getName());
		assertEquals(15.10, indexedDoc.getNumberFacetData().get(0).getValue());

		assertEquals("document c", ((List<String>) indexedDoc.getSortData().get("title")).get(0));
		assertEquals(15.10, ((List<Double>) indexedDoc.getSortData().get("price")).get(0));
	}

	@Test
	public void patchScoresField() throws Exception {
		String indexName = "patch_test";
		String id = "d1";
		Document doc = new Document(id);
		doc.data.put("title", "document d");
		doc.data.put("stock", 5);
		putDocument(indexName, doc);
		assertIndexExists(indexName, true);

		// new document only with partial data
		Document docPatch = new Document(id);
		docPatch.data.put("stock", 1);
		patchDocument(indexName, docPatch);

		IndexableItem indexedDoc = getIndexedDocument(indexName, id);
		assertNotNull(indexedDoc);

		assertEquals("document d", joinToString(indexedDoc.getSortData().get("title"), ' '));
		assertEquals(1, indexedDoc.getScores().get("stock"));
	}

	@Test
	public void patchCategoryField() throws Exception {
		String indexName = "patch_test";
		String id = "e1";
		Document doc = new Document(id);
		doc.data.put("title", "document e");
		doc.addCategory(new Category("c1", "sale"), new Category("c1.2", "stuff"));
		putDocument(indexName, doc);
		assertIndexExists(indexName, true);

		Document docPatch = new Document(id);
		docPatch.addCategory(new Category("c1", "sale"), new Category("c1.3", "other stuff"));
		patchDocument(indexName, docPatch);

		IndexableItem indexedDoc = getIndexedDocument(indexName, id);
		assertNotNull(indexedDoc);
		assertEquals("category", indexedDoc.getPathFacetData().get(0).getName());
		assertEquals("sale", indexedDoc.getPathFacetData().get(0).getValue());
		assertEquals("c1", indexedDoc.getPathFacetData().get(0).getId());

		assertEquals("category", indexedDoc.getPathFacetData().get(1).getName());
		assertEquals("sale/other stuff", indexedDoc.getPathFacetData().get(1).getValue());
		assertEquals("c1.3", indexedDoc.getPathFacetData().get(1).getId());
	}

	@Test
	public void patchVariantField() throws Exception {
		String indexName = "patch_test";
		String id = "f1";
		Product prod = new Product(id);
		prod.set("title", "document e");
		prod.set("productUrl", "http://main/" + id);
		prod.setVariants(new Document[] {
				new Document().set("id", "f.v1").set("productUrl", "http://variant/v1"),
				new Document().set("id", "f.v2").set("productUrl", "http://variant/v2")
		});
		putDocument(indexName, prod);
		assertIndexExists(indexName, true);

		Product patchProduct = new Product(id);
		patchProduct.setVariants(new Document[] {
				new Document().set("id", "f.v2").set("productUrl", "http://variant/v2_patched")
		});
		patchDocument(indexName, patchProduct);
		IndexableItem indexedDoc = getIndexedDocument(indexName, id);
		assertNotNull(indexedDoc);
		assertTrue(indexedDoc instanceof MasterItem);
		MasterItem indexedMaster = (MasterItem) indexedDoc;

		assertEquals(2, indexedMaster.getVariants().size());

		VariantItem variant1 = indexedMaster.getVariants().get(0);
		assertEquals("f.v1", variant1.getResultData().get("id"));
		assertEquals("http://variant/v1", variant1.getResultData().get("productUrl"));

		VariantItem variant2 = indexedMaster.getVariants().get(1);
		assertEquals("f.v2", variant2.getResultData().get("id"));
		assertEquals("http://variant/v2_patched", variant2.getResultData().get("productUrl"));
	}

	@Test
	public void deleteDocument() throws Exception {
		String indexName = "patch_test";
		String id = "g1";
		Document doc = new Document(id);
		doc.data.put("title", "document g");
		putDocument(indexName, doc);
		assertIndexExists(indexName, true);

		deleteDocument(indexName, id);

		IndexableItem indexedDoc = getIndexedDocument(indexName, id);
		assertNull(indexedDoc);
	}

	@Test
	public void deleteNonExistingDoc() throws Exception {
		String indexName = "patch_test";
		String id = "h1";
		Document doc = new Document(id).set("title", "document h");
		putDocument(indexName, doc);
		assertIndexExists(indexName, true);

		deleteDocument(indexName, "other", 200, Result.NOT_FOUND);
	}

	void assertIndexExists(String indexName, boolean exists) throws Exception {
		boolean actualExists = esClient.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
		assertEquals(exists, actualExists);
	}

	private void putDocument(String indexName, Document doc) throws Exception {
		putDocument(indexName, doc, 200, Result.CREATED);
	}

	void putDocument(String indexName, Document doc, int expectedStatus, Result expectedResult) throws Exception {
		String docBody = objectMapper.writeValueAsString(Collections.singletonList(doc));
		mockMvc.perform(MockMvcRequestBuilders
				.put("/indexer-api/v1/update/" + indexName + "?langCode=en")
				.contentType(MediaType.APPLICATION_JSON)
				.content(docBody))
				.andExpect(MockMvcResultMatchers.status().is(expectedStatus))
				.andExpect(MockMvcResultMatchers.content()
						.string(objectMapper.writeValueAsString(Collections.singletonMap(doc.id, expectedResult))));
	}

	private void patchDocument(String indexName, Document doc) throws Exception {
		patchDocument(indexName, doc, 200, Result.UPDATED);
	}

	void patchDocument(String indexName, Document doc, int expectedStatus, Result expectedResult) throws Exception {
		String docBody = objectMapper.writeValueAsString(Collections.singletonList(doc));
		mockMvc.perform(MockMvcRequestBuilders
				.patch("/indexer-api/v1/update/" + indexName)
				.contentType(MediaType.APPLICATION_JSON)
				.content(docBody))
				.andExpect(MockMvcResultMatchers.status().is(expectedStatus))
				.andExpect(MockMvcResultMatchers.content()
						.string(objectMapper.writeValueAsString(Collections.singletonMap(doc.id, expectedResult))));
	}

	void deleteDocument(String indexName, String id) throws Exception {
		deleteDocument(indexName, id, 200, Result.DELETED);
	}

	void deleteDocument(String indexName, String id, int expectedStatus, Result expectedResult) throws Exception {
		mockMvc.perform(MockMvcRequestBuilders
				.delete("/indexer-api/v1/update/" + indexName + "?id=" + id)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(MockMvcResultMatchers.status().is(expectedStatus))
				.andExpect(MockMvcResultMatchers.content()
						.string(objectMapper.writeValueAsString(Collections.singletonMap(id, expectedResult))));
	}

	IndexableItem getIndexedDocument(String indexName, String id) throws IOException {
		GetResponse esDoc = esClient.get(new GetRequest(indexName, id), RequestOptions.DEFAULT);
		if (!esDoc.isExists()) return null;

		Map<String, Object> source = esDoc.getSource();
		Object variantsSources = source.get(VARIANTS);
		IndexableItem indexedItem;

		if (variantsSources != null && variantsSources instanceof List && !((List<?>) variantsSources).isEmpty()) {
			MasterItem masterItem = new MasterItem(id);
			for (Object variantSource : (List<?>) variantsSources) {
				VariantItem variantItem = new VariantItem(masterItem);
				applyData(variantItem, castMapData(variantSource));
				masterItem.getVariants().add(variantItem);
			}
			indexedItem = masterItem;
		}
		else {
			indexedItem = new IndexableItem(id);
		}

		applyData(indexedItem, source);
		Optional.ofNullable(source.get(PATH_FACET_DATA)).map(d -> readFacetData(d, String.class)).ifPresent(indexedItem.getPathFacetData()::addAll);

		return indexedItem;
	}

	private void applyData(DataItem indexedItem, Map<String, Object> source) {
		Optional.ofNullable(source.get(SEARCH_DATA)).map(this::castMapData).ifPresent(indexedItem.getSearchData()::putAll);
		Optional.ofNullable(source.get(RESULT_DATA)).map(this::castMapData).ifPresent(indexedItem.getResultData()::putAll);
		Optional.ofNullable(source.get(SCORES)).map(this::castMapData).ifPresent(indexedItem.getScores()::putAll);
		Optional.ofNullable(source.get(SORT_DATA)).map(this::castMapData).ifPresent(indexedItem.getSortData()::putAll);
		Optional.ofNullable(source.get(NUMBER_FACET_DATA)).map(d -> readFacetData(d, Number.class)).ifPresent(indexedItem.getNumberFacetData()::addAll);
		Optional.ofNullable(source.get(TERM_FACET_DATA)).map(d -> readFacetData(d, String.class)).ifPresent(indexedItem.getTermFacetData()::addAll);
	}

	private HashMap<String, Object> castMapData(Object data) {
		return (HashMap<String, Object>) data;
	}

	private <T> List<FacetEntry<T>> readFacetData(Object data, Class<T> facetValueType) {
		List<FacetEntry<T>> facetList = new ArrayList<>();
		for (Object facetEntryObj : ((List<?>) data)) {
			HashMap<String, Object> facetEntryMap = castMapData(facetEntryObj);
			facetList.add(
					new FacetEntry<T>(
							facetEntryMap.get("name").toString(),
							Optional.ofNullable(facetEntryMap.get("id")).map(Object::toString).orElse(null),
							facetValueType.cast(facetEntryMap.get("value"))));
		}
		return facetList;
	}

	private String joinToString(Object object, char separator) {
		if (object instanceof Iterable<?>) {
			return StringUtils.join(((Iterable<?>) object), separator);
		}
		else if (object.getClass().isArray()) {
			return StringUtils.join((Object[]) object, separator);
		}
		else {
			return object.toString();
		}
	}
}
