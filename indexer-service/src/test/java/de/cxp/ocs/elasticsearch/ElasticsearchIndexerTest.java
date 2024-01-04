package de.cxp.ocs.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.core.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.config.*;

@SuppressWarnings("deprecation")
public class ElasticsearchIndexerTest {

	private ElasticsearchIndexer underTest;
	private RestHighLevelClient				indexClient;
	private static ElasticsearchContainer	container;

	@BeforeAll
	public static void startElasticsearch() {
		container = ElasticsearchContainerUtil.spinUpEs();
	}

	@AfterAll
	public static void shutdown() {
		if (container != null && container.isRunning()) container.stop();
	}
	
	@BeforeEach
	public void initIndexer() {
		// set up minimal field config, with title
		FieldConfiguration fieldConfig = new FieldConfiguration();
		fieldConfig.addField(new Field("title").setUsage(FieldUsage.SEARCH, FieldUsage.RESULT));
		FieldConfigIndex fieldConfigIndex = new FieldConfigIndex(fieldConfig);
		IndexSettings settings = new IndexSettings();
		settings.setMinimumDocumentCount(0);
		indexClient = ElasticsearchContainerUtil.initClient(container);
		underTest = new ElasticsearchIndexer(settings, fieldConfigIndex, indexClient, List.of(), List.of());
	}

	@Test
	public void testSuccessfulLifecycle() throws Exception {
		String indexName = "test1";
		String locale = "en";
		assertFalse(underTest.indexExists(indexName, locale));

		ImportSession importSession = underTest.startImport(indexName, locale);
		assertEquals("ocs-1-test1-en", importSession.temporaryIndexName);

		assertTrue(underTest.indexExists(indexName, locale));
		assertTrue(underTest.isImportRunning(indexName, locale));

		assertTrue(underTest.done(importSession));

		assertTrue(underTest.indexExists(indexName, locale));
		assertFalse(underTest.isImportRunning(indexName, locale));

		underTest.deleteIndex(importSession.temporaryIndexName);
		assertFalse(underTest.indexExists(indexName, locale));
	}

	@Test
	public void testCleanupOfAbandonedIndexes() throws IOException, InterruptedException {
		String indexName = "test2";
		String locale = "en";
		assertFalse(underTest.indexExists(indexName, locale));

		String abandonedIndex = "ocs-1-" + indexName + "-" + locale;
		indexClient.indices().create(new CreateIndexRequest(abandonedIndex), RequestOptions.DEFAULT);
		assertTrue(underTest.indexExists(indexName, locale));

		underTest.setAbandonedIndexDeletionAgeSeconds(1);
		Thread.sleep(1000);

		// starting a new import should create a new index and cleanup the abandoned one
		ImportSession importSession = underTest.startImport(indexName, locale);
		assertEquals("ocs-2-" + indexName + "-" + locale, importSession.temporaryIndexName);

		// cleanup runs async, so wait one more second
		Thread.sleep(1000);
		assertFalse(indexClient.indices().exists(new GetIndexRequest().indices(abandonedIndex), RequestOptions.DEFAULT));
		assertTrue(underTest.indexExists(indexName, locale)); // index 2 still exists
	}

}
