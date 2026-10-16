package de.cxp.ocs.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.client.IndicesClient;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.common.settings.Settings;
import org.junit.jupiter.api.Test;

class ElasticsearchIndexClientFailureTest {

	@Test
	void ioErrorsAreNotSwallowed() throws Exception {
		RestHighLevelClient client = mock(RestHighLevelClient.class);
		IndicesClient indices = mock(IndicesClient.class);
		when(client.indices()).thenReturn(indices);
		when(indices.create(any(CreateIndexRequest.class), any(RequestOptions.class))).thenThrow(new IOException("connection reset"));
		when(indices.updateAliases(any(IndicesAliasesRequest.class), any(RequestOptions.class))).thenThrow(new IOException("connection reset"));

		ElasticsearchIndexClient underTest = new ElasticsearchIndexClient(client);
		assertThrows(UncheckedIOException.class, () -> underTest.createFreshIndex("ocs-1-x-en"));
		assertThrows(UncheckedIOException.class, () -> underTest.updateAlias("x", null, "ocs-1-x-en"));
	}

	@Test
	void indexWithUnknownCreationDateIsNotDeleted() {
		ElasticsearchIndexClient indexClient = mock(ElasticsearchIndexClient.class);
		Map<String, Set<AliasMetadata>> aliases = Map.of("ocs-1-x-en", Set.of());
		when(indexClient.getAliases(anyString())).thenReturn(aliases);
		when(indexClient.getSettings(anyString())).thenReturn(Optional.of(Settings.EMPTY));
		new AbandonedIndexCleanupTask(indexClient, "x", 1).run();
		verify(indexClient, never()).deleteIndex(anyString(), anyBoolean());
	}
}
