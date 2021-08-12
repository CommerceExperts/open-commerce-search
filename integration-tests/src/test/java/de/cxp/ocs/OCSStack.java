package de.cxp.ocs;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import de.cxp.ocs.client.ImportClient;
import de.cxp.ocs.client.SearchClient;
import de.cxp.ocs.client.SuggestClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OCSStack implements BeforeAllCallback {

	private final static int	ES_DEFAULT_PORT			= 9200;
	private final static int	INDEXER_DEFAULT_PORT	= 8535;
	private final static int	SEARCH_DEFAULT_PORT		= 8534;
	private final static int	SUGGEST_DEFAULT_PORT	= 8081;

	public static AtomicBoolean started = new AtomicBoolean(false);

	private static ImportClient		importClient;
	private static SearchClient		searchClient;
	private static SuggestClient	suggestClient;
	private static RestClient		esRestClient;

	private ElasticsearchContainer	elasticsearch;
	private GenericContainer<?>		indexerService;
	private GenericContainer<?>		suggestService;
	private GenericContainer<?>		searchService;

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		if (started.compareAndSet(false, true)) {
			log.info("starting OCS-Stack for testing");

			CompletableFuture<HttpHost> esHost;
			HttpHost esDebugHost = null;
			// in case a component should be debugged and not run in a
			// temporary container, just specify the according host
			// adress as env variable
			if (System.getenv("ES_DEBUG_HOST") != null) {
				esDebugHost = HttpHost.create(System.getenv("ES_DEBUG_HOST"));

				// If ES is running on host, we need expose that host port to
				// the other test containers
				// https://www.testcontainers.org/features/networking/#exposing-host-ports-to-the-container
				if ("localhost".equals(esDebugHost.getHostName())) {
					Testcontainers.exposeHostPorts(esDebugHost.getPort());
					esHost = CompletableFuture.completedFuture(new HttpHost("host.testcontainers.internal", esDebugHost.getPort()));
				}
				else {
					esHost = CompletableFuture.completedFuture(esDebugHost);
				}
			}
			else {
				elasticsearch = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:7.10.2");
				elasticsearch.setExposedPorts(Collections.singletonList(ES_DEFAULT_PORT));
				elasticsearch.withNetwork(Network.newNetwork());
				elasticsearch.withNetworkAliases("elasticsearch");

				esHost = CompletableFuture.supplyAsync(() -> {
					elasticsearch.start();
					return new HttpHost("localhost", elasticsearch.getMappedPort(ES_DEFAULT_PORT));
				});
			}

			CompletableFuture<String> indexerHost = startIndexerService(esHost);
			CompletableFuture<String> searchServiceHost = startSearchService(esHost);
			CompletableFuture<String> suggestServiceHost = startSuggestService(esHost);

			esRestClient = RestClient.builder(esDebugHost != null ? esDebugHost : esHost.get()).build();
			importClient = new ImportClient(indexerHost.get());
			searchClient = new SearchClient(searchServiceHost.get());
			suggestClient = new SuggestClient(suggestServiceHost.get());

			log.info("OCS-Stack started");
		}
	}

	private CompletableFuture<String> startIndexerService(CompletableFuture<HttpHost> esHost) throws InterruptedException, ExecutionException {
		CompletableFuture<String> indexerHost;
		if (System.getenv("INDEXER_DEBUG_HOST") != null) {
			indexerHost = CompletableFuture.completedFuture(System.getenv("INDEXER_DEBUG_HOST"));
		}
		else {
			indexerService = new GenericContainer<>("commerceexperts/ocs-indexer-service:latest");
			indexerService.addExposedPort(INDEXER_DEFAULT_PORT);
			indexerService.addEnv("JAVA_TOOL_OPTIONS", "-Dspring.cloud.config.enabled=false -Dspring.profiles.active=default,preset");

			if (elasticsearch != null) {
				indexerService.setNetwork(elasticsearch.getNetwork());
				indexerService.addEnv("ES_HOSTS", "http://elasticsearch:9200");
			}
			else {
				indexerService.addEnv("ES_HOSTS", esHost.get().toURI());
			}

			indexerHost = CompletableFuture.supplyAsync(() -> {
				indexerService.start();
				return "http://localhost:" + indexerService.getMappedPort(INDEXER_DEFAULT_PORT);
			});
		}
		return indexerHost;
	}

	private CompletableFuture<String> startSearchService(CompletableFuture<HttpHost> esHost) throws InterruptedException, ExecutionException {
		CompletableFuture<String> searchServiceHost;
		if (System.getenv("SEARCH_SERVICE_DEBUG_HOST") != null) {
			searchServiceHost = CompletableFuture.completedFuture(System.getenv("SEARCH_SERVICE_DEBUG_HOST"));
		}
		else {
			searchService = new GenericContainer<>("commerceexperts/ocs-search-service:latest");
			searchService.addExposedPort(SEARCH_DEFAULT_PORT);
			// searchService.setCommand("-Dspring.cloud.config.enabled=false",
			// "-Dspring.profiles.active=preset");
			searchService.addEnv("JAVA_TOOL_OPTIONS", "-Dspring.cloud.config.enabled=false -Dspring.profiles.active=default,preset,trace-searches");

			if (elasticsearch != null) {
				searchService.setNetwork(elasticsearch.getNetwork());
				searchService.addEnv("ES_HOSTS", "http://elasticsearch:9200");
			}
			else {
				searchService.addEnv("ES_HOSTS", esHost.get().toURI());
			}

			searchServiceHost = CompletableFuture.supplyAsync(() -> {
				searchService.start();
				return "http://localhost:" + searchService.getMappedPort(SEARCH_DEFAULT_PORT);
			});
		}
		return searchServiceHost;
	}

	private CompletableFuture<String> startSuggestService(CompletableFuture<HttpHost> esHost) throws InterruptedException, ExecutionException {
		CompletableFuture<String> suggestServiceHost;
		if (System.getenv("SUGGEST_SERVICE_DEBUG_HOST") != null) {
			suggestServiceHost = CompletableFuture.completedFuture(System.getenv("SUGGEST_SERVICE_DEBUG_HOST"));
		}
		else {
			suggestService = new GenericContainer<>("commerceexperts/ocs-suggest-service:latest");
			suggestService.addExposedPort(SUGGEST_DEFAULT_PORT);

			String esAddr;
			if (elasticsearch != null) {
				suggestService.setNetwork(elasticsearch.getNetwork());
				esAddr = "http://elasticsearch:9200";
			}
			else {
				suggestService.setNetwork(Network.SHARED);
				esAddr = esHost.get().toURI();
			}

			suggestService.addEnv("JAVA_TOOL_OPTIONS",
							"-Delasticsearch.hosts=" + esAddr +
							" -Dsuggest.index.default.sourceFields=brand,category" +
							" -Dsuggest.group.key=type");

			suggestServiceHost = CompletableFuture.supplyAsync(() -> {
				suggestService.start();
				return "http://localhost:" + suggestService.getMappedPort(SUGGEST_DEFAULT_PORT);
			});
		}
		return suggestServiceHost;
	}

	public static RestClient getElasticsearchClient() {
		assert started.get() : "Stack not started yet!";
		return esRestClient;
	}

	public static ImportClient getImportClient() {
		assert started.get() : "Stack not started yet!";
		return importClient;
	}

	public static SearchClient getSearchClient() {
		assert started.get() : "Stack not started yet!";
		return searchClient;
	}

	public static SuggestClient getSuggestClient() {
		assert started.get() : "Stack not started yet!";
		return suggestClient;
	}

	// all containers will be shutdown automatically by 'testcontainers'
}
