package de.cxp.ocs.elasticsearch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.DocWriteRequest.OpType;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.xcontent.XContentType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.cxp.ocs.indexer.model.IndexableItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Convenient class that wraps elasticsearch requests around all indexation
 * tasks.
 */
@Slf4j
@RequiredArgsConstructor
class ElasticsearchIndexClient {

	public static final String RECORD_TYPE = "_doc";

	public static final String	ES_SETTINGS_NUMBER_OF_REPLICAS	= "index.number_of_replicas";
	public static final String	ES_SETTINGS_REFRESH_INTERVAL	= "index.refresh_interval";

	private final RestHighLevelClient	highLevelClient;
	private final ObjectMapper			mapper	= IndexableItemMapperFactory.createObjectMapper();

	/**
	 * Get actual index names with potential aliases.
	 * 
	 * @param indexNames
	 * @return
	 */
	public Map<String, Set<AliasMetadata>> getAliases(String indexName) {
		try {
			GetAliasesRequest getAliasesRequest = new GetAliasesRequest().indices(indexName);
			GetAliasesResponse response = highLevelClient
					.indices()
					.getAlias(getAliasesRequest, RequestOptions.DEFAULT);

			return response.getAliases();
		}
		catch (IOException e) {
			log.error("Could not get alias for index {} due to {}: {}", indexName, e.getClass().getSimpleName(), e.getMessage());
			throw new UncheckedIOException(e);
		}
	}

	public void updateAlias(String aliasName, String oldIndexName, String newIndexName) {
		IndicesAliasesRequest aliasRequest = new IndicesAliasesRequest();
		if (oldIndexName != null) {
			aliasRequest.addAliasAction(AliasActions.remove().alias(aliasName).index(oldIndexName));
		}
		aliasRequest.addAliasAction(AliasActions.add().alias(aliasName).index(newIndexName));

		try {
			AcknowledgedResponse updateAliases = highLevelClient.indices()
					.updateAliases(aliasRequest, RequestOptions.DEFAULT);
		}
		catch (IOException e) {
			log.error("updating alias {} from old index {} to new index {} failed because of {}: {}",
					aliasName, oldIndexName, newIndexName,
					e.getClass().getSimpleName(), e.getMessage());
		}
	}

	/**
	 * create index ready for full indexation. After indexation finalizeIndex should be called to enable replication and set refresh interval.
	 *
	 * @param indexName
	 */
	public void createFreshIndex(String indexName) {
		CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName);
		createIndexRequest.settings(Settings.builder()
				.put(ES_SETTINGS_NUMBER_OF_REPLICAS, 0)
				.put(ES_SETTINGS_REFRESH_INTERVAL, "-1"));
		try {
			highLevelClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
		}
		catch (IOException e) {
			log.error("creating index {} failed: {}", indexName, e.getMessage());
		}
	}

	/**
	 * Finalize index after full import by increasing number of replicas and
	 * apply a proper refresh interval. Afterwards flushes the index waiting for
	 * it to become available.
	 * 
	 * @param indexName
	 * @param numberOfReplicas
	 * @param refreshInterval
	 * @return
	 * @throws IOException
	 */
	public boolean finalizeIndex(String indexName, int numberOfReplicas, String refreshInterval) throws IOException {
		boolean success = applyIndexSettings(indexName, numberOfReplicas, refreshInterval);

		// refresh immediately
		RefreshResponse refresh = highLevelClient.indices().refresh(new RefreshRequest(indexName), RequestOptions.DEFAULT);

		if (refresh.getFailedShards() > 0) {
			log.error("Failed to refresh index. {} out of {} shards failed.",
					refresh.getFailedShards(), refresh.getTotalShards());
			return false;
		}
		return success;
	}

	private boolean applyIndexSettings(String indexName, int numberOfReplicas, String refreshInterval) {
		UpdateSettingsRequest request = new UpdateSettingsRequest(indexName);
		request.indicesOptions(IndicesOptions.lenientExpandOpen());

		Settings settings = Settings.builder()
				.put(ES_SETTINGS_NUMBER_OF_REPLICAS, numberOfReplicas)
				.put(ES_SETTINGS_REFRESH_INTERVAL, refreshInterval)
				.build();

		request.settings(settings);
		try {
			AcknowledgedResponse updateSettingsResponse = highLevelClient.indices().putSettings(request, RequestOptions.DEFAULT);
			return updateSettingsResponse.isAcknowledged();
		}
		catch (ElasticsearchException | IOException e) {
			log.error("Failed to set index settings {}:{}, {}:{} because of {}:{}.",
					ES_SETTINGS_NUMBER_OF_REPLICAS, numberOfReplicas,
					ES_SETTINGS_REFRESH_INTERVAL, refreshInterval,
					e.getClass().getSimpleName(), e.getMessage());
		}
		return false;
	}

	public ClusterHealthStatus waitUntilHealthy(String indexName, int timeoutMillis) {
		long start = System.currentTimeMillis();
		ClusterHealthResponse health = null;
		do {
			try {
				health = highLevelClient.cluster().health(new ClusterHealthRequest(indexName), RequestOptions.DEFAULT);
				Thread.sleep(50);
			}
			catch (IOException e) {
				log.warn("Could not fetch health status for index {} because of {}", indexName, e.getMessage());
			}
			catch (InterruptedException e) {
				log.info("Interrupted waiting for healthy index {}", indexName);
				break;
			}
		}
		while (!ClusterHealthStatus.GREEN.equals(health.getStatus()) && (System.currentTimeMillis() - start) < timeoutMillis);
		return health == null ? ClusterHealthStatus.RED : health.getStatus();
	}

	/**
	 * Queries Elasticsearch for all documents contained in the newly created
	 * index.
	 * 
	 * @return the document count of the index.
	 * @throws IOException
	 */
	public long getDocCount(String indexName) throws IOException {
		CountResponse count = highLevelClient.count(new CountRequest(indexName), RequestOptions.DEFAULT);
		return count.getCount();
	}

	public void deleteIndex(final String index, final boolean silent) {
		try {
			DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(index);
			AcknowledgedResponse deleteIndexResponse = highLevelClient.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
			if (!deleteIndexResponse.isAcknowledged()) {
				if (!silent) {
					throw new IOException("Delete index operation not acknowledged by client response.");
				}
			}
		}
		catch (Exception e) {
			if (!silent) {
				log.warn("Failed to delete old index {} because the index did not exist.", index);
			}
		}
	}

	/**
	 * Index a single record. Does not handle already existing indices, instead
	 * it just re indexes the record. To index several records at once, please
	 * use indexRecords() or indexRecordsChunkwise() instead.
	 * 
	 * @param record
	 * @param opType
	 * @return
	 * @throws IOException
	 */
	public IndexResponse indexRecord(String indexName, IndexableItem record, OpType opType) throws IOException {
		IndexRequest indexRequest = asIndexRequest(indexName, record);
		indexRequest.opType(opType);
		return highLevelClient.index(indexRequest, RequestOptions.DEFAULT);
	}

	/**
	 * Will apply all record inside one single bulk request. The target index
	 * will be delete before if already present.
	 * 
	 * @param items
	 * @return
	 * @throws IOException
	 */
	public Optional<BulkResponse> indexRecords(String indexName, List<IndexableItem> items) throws IOException {
		BulkRequest bulkIndexRequest = new BulkRequest();
		int docCount = 0;
		for (IndexableItem item : items) {
			IndexRequest indexRequest;
			try {
				indexRequest = asIndexRequest(indexName, item);
				bulkIndexRequest.add(indexRequest);
				docCount++;
			}
			catch (JsonProcessingException e) {
				log.warn("failed to add record to bulk request", e);
			}
		}
		if (docCount > 0) {
			return Optional.ofNullable(highLevelClient.bulk(bulkIndexRequest, RequestOptions.DEFAULT));
		}
		else return Optional.empty();
	}

	private IndexRequest asIndexRequest(String indexName, final IndexableItem record)
			throws JsonProcessingException {
		IndexRequest indexRequest = new IndexRequest(indexName).id(record.getId());
		indexRequest.source(mapper.writeValueAsBytes(record), XContentType.JSON);
		return indexRequest;
	}

	/**
	 * Will split (if necessary) the given records into several bulk requests
	 * each with the specified maximum size. The target index will be delete
	 * before if already present.
	 * 
	 * @param records
	 * @param maxBulkSize
	 * @return
	 * @throws IOException
	 */
	public List<BulkResponse> indexRecordsChunkwise(String indexName, Iterator<IndexableItem> records, int maxBulkSize)
			throws IOException {
		List<BulkResponse> responses = new ArrayList<>();
		BulkRequest bulkIndexRequest = new BulkRequest();
		int i = 0;
		int indexedTotal = 0;
		while (records.hasNext()) {
			IndexRequest indexRequest;
			try {
				IndexableItem nextRecord = records.next();
				if (nextRecord != null) {
					indexRequest = asIndexRequest(indexName, nextRecord);
					bulkIndexRequest.add(indexRequest);
					i++;
					if (i == maxBulkSize) {
						indexedTotal += i;
						i = 0;
						responses.add(highLevelClient.bulk(bulkIndexRequest, RequestOptions.DEFAULT));
						log.info("Indexed {} records", indexedTotal);
						bulkIndexRequest = new BulkRequest();
					}
				}
			}
			catch (JsonProcessingException e) {
				log.warn("failed to add record to bulk request", e);
			}
		}
		if (i > 0) {
			responses.add(highLevelClient.bulk(bulkIndexRequest, RequestOptions.DEFAULT));
		}
		return responses;
	}

	public Optional<Settings> getSettings(String indexName) {
		GetSettingsResponse settings;
		try {
			settings = highLevelClient.indices()
					.getSettings(new GetSettingsRequest().indices(indexName), RequestOptions.DEFAULT);
		}
		catch (RuntimeException | IOException e) {
			log.warn("couldn't get settings for index {}: IOException: {}", indexName, e.getMessage());
			return Optional.empty();
		}
		return Optional.ofNullable(settings.getIndexToSettings().get(indexName));
	}

	public UpdateResponse updateDocument(String index, IndexableItem doc) throws IOException {
		UpdateRequest updateRequest = new UpdateRequest(index, doc.getId());
		updateRequest.doc(asIndexRequest(index, doc));
		return highLevelClient.update(updateRequest, RequestOptions.DEFAULT);
	}

	public DeleteResponse deleteDocument(String index, String id) throws IOException {
		DeleteRequest deleteRequest = new DeleteRequest(index, id);
		return highLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
	}

	public List<DeleteResponse> deleteDocuments(String index, List<String> ids) throws IOException {
		BulkRequest bulkRequest = new BulkRequest(index);
		for (String id : ids) {
			bulkRequest.add(new DeleteRequest().id(id));
		}
		BulkResponse bulkResponse = highLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);

		List<DeleteResponse> responses = new ArrayList<>();
		for (BulkItemResponse item : bulkResponse.getItems()) {
			if (item.isFailed()) {
				if (RestStatus.NOT_FOUND.equals(item.getFailure().getStatus())) {
					throw new ElasticsearchStatusException(item.getFailureMessage(), item.getFailure().getStatus(),
							item.getFailure().getCause());
				}
			} else {
				responses.add((DeleteResponse) item.getResponse());
			}
		}
		return responses;
	}
}
