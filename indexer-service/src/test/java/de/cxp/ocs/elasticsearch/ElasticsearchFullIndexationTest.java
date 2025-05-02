package de.cxp.ocs.elasticsearch;

import static de.cxp.ocs.config.FieldType.CATEGORY;
import static de.cxp.ocs.config.FieldType.ID;
import static de.cxp.ocs.config.FieldType.STRING;
import static de.cxp.ocs.config.FieldUsage.FACET;
import static de.cxp.ocs.config.FieldUsage.RESULT;
import static de.cxp.ocs.config.FieldUsage.SEARCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.internal.matchers.Equals;

import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.conf.IndexConfiguration;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.model.index.BulkImportData;
import de.cxp.ocs.model.index.Category;
import de.cxp.ocs.model.index.Document;

public class ElasticsearchFullIndexationTest {

	final ElasticsearchIndexClient mockedIndexClient = mock(ElasticsearchIndexClient.class);

	final ElasticsearchIndexer underTest = new ElasticsearchIndexer(
			new FieldConfigIndex(getIndexConf().getFieldConfiguration()),
			mockedIndexClient,
			Collections.emptyList(),
			Collections.emptyList());

	@BeforeEach
	public void setupDefaultEsIndexClient() {
		when(mockedIndexClient.getSettings(any())).thenReturn(Optional.empty());
	}

	@Test
	public void testStandardIndexProcess() throws Exception {
		ImportSession importSession = underTest.startImport("test", "de");
		assertEquals(importSession.finalIndexName, "test");
		assertTrue(importSession.temporaryIndexName.endsWith("de"));

		BulkImportData data = new BulkImportData();
		data.setSession(importSession);
		data.setDocuments(new Document[] {
				new Document().setId("1").set("title", "Test 1"),
				new Document().setId("2").set("title", "Test 2")
						.setCategories(Collections.singletonList(new Category[] { new Category("c1", "cat1"), new Category("c2", "cat2") }))
		});

		when(mockedIndexClient.indexRecords(any(), any())).thenReturn(Optional.empty());
		underTest.add(data);
		verify(mockedIndexClient).indexRecords((String) argThat(new Equals(importSession.temporaryIndexName)), any());

		when(mockedIndexClient.getDocCount(importSession.temporaryIndexName)).thenReturn(2L);
		underTest.done(importSession);
		verify(mockedIndexClient).updateAlias(importSession.finalIndexName, null, importSession.temporaryIndexName);
	}


	@Test
	public void testImportSessionStartsWhileOtherNotFinished() {
		when(mockedIndexClient.getAliases(ArgumentMatchers.startsWith("ocs-*-test")))
				.thenReturn(Collections.singletonMap("ocs-1-test-de", Collections.emptySet()));
		ImportSession importSession = underTest.startImport("test", "de");
		assertEquals("ocs-2-test-de", importSession.temporaryIndexName);
	}

	private IndexConfiguration getIndexConf() {
		IndexConfiguration config = new IndexConfiguration();
		config.getFieldConfiguration()
				.addField(new Field("id").setType(ID).setUsage(RESULT))
				.addField(new Field("title").setType(STRING).setUsage(RESULT, SEARCH))
				.addField(new Field("cagories").setType(CATEGORY).setUsage(RESULT, SEARCH, FACET));
		return config;
	}

}
