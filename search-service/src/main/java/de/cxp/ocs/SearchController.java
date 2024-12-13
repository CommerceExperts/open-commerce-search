package de.cxp.ocs;

import static de.cxp.ocs.util.SearchParamsParser.extractInternalParams;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import de.cxp.ocs.api.searcher.SearchService;
import de.cxp.ocs.elasticsearch.ElasticSearchBuilder;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.elasticsearch.mapper.ResultMapper;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.params.ArrangedSearchQuery;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.util.InternalSearchParams;
import de.cxp.ocs.util.NotFoundException;
import de.cxp.ocs.util.TraceOptions.TraceFlag;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@RefreshScope
@CrossOrigin("*")
@RestController
@RequestMapping(path = "/search-api/v1")
@EnableScheduling
@Slf4j
public class SearchController implements SearchService {

	@Autowired
	@NonNull
	private ElasticSearchBuilder esBuilder;

	@Autowired
	@NonNull
	private SearchPlugins plugins;

	@Autowired
	@NonNull
	private SearchContextLoader searchContextLoader;

	@Autowired
	private MeterRegistry registry;

	@Autowired
	private ContextRefresher contextRefresher;

	private final Map<String, SearchContext> searchContexts = new ConcurrentHashMap<>();

	private final Map<String, String> actualIndexPerTenant = new ConcurrentHashMap<>();

	private final Cache<String, Searcher> searchClientCache = CacheBuilder.newBuilder()
			.expireAfterAccess(10, TimeUnit.MINUTES)
			.removalListener(notification -> searchContexts.remove(notification.getKey()))
			.build();

	private final Cache<String, Exception> brokenTenantsCache = CacheBuilder.newBuilder()
			.expireAfterWrite(5, TimeUnit.MINUTES)
			.maximumSize(64)
			.build();

	@Scheduled(fixedDelayString = "${ocs.scheduler.refresh-config-delay-ms:60000}")
	public void refreshAllConfigs() {
		contextRefresher.refresh();
		Set<String> loadedTenants = new HashSet<>(searchContexts.keySet());
		if (loadedTenants.size() > 0) {
			log.info("Refreshing {} loaded tenants: {}", loadedTenants.size(), loadedTenants);
			loadedTenants.forEach(this::flushConfig);
		}
	}

	@Operation(summary = "Reload configuration for specified tenant.", responses = {
			@ApiResponse(responseCode = "200", description = "successful reloaded configuration for tenant"),
			@ApiResponse(responseCode = "201", description = "Config was loaded first time for tenant"),
			@ApiResponse(responseCode = "304", description = "No config changes detected. Nothing to reload.") })
	@GetMapping("/flushConfig/{tenant}")
	public ResponseEntity<HttpStatus> flushConfig(@PathVariable("tenant") String tenant) {
		HttpStatus status;
		synchronized (tenant.intern()) {
			MDC.put("tenant", tenant);
			try {
				brokenTenantsCache.invalidate(tenant);
				SearchContext searchContext = searchContextLoader.loadContext(tenant);
				SearchContext oldConfig = searchContexts.put(tenant, searchContext);
				if (oldConfig == null) {
					log.info("config successfuly loaded for tenant {}", tenant);
					status = HttpStatus.CREATED;
				} else if (oldConfig.equals(searchContexts.get(tenant))) {
					log.info("config flush did not modify config for tenant {}", tenant);
					status = HttpStatus.NOT_MODIFIED;
				} else {
					log.info("config successfuly reloaded for tenant {}", tenant);
					status = HttpStatus.OK;
					searchClientCache.put(tenant, initializeSearcher(searchContext));
				}
			}
			catch (ElasticsearchStatusException esx) {
				try {
					handleUnavailableIndex(tenant, esx);
					// if this method does not throw a NotFoundException
					// the error is about something else
					log.error("Error while flushing config for tenant {}", tenant, esx);
					status = HttpStatus.INTERNAL_SERVER_ERROR;
				}
				catch (NotFoundException notFound) {
					status = HttpStatus.NOT_FOUND;
				}
			}

			MDC.remove("tenant");
		}

		return new ResponseEntity<>(status, status);
	}

	@GetMapping("/search/{tenant}")
	@Override
	public SearchResult search(@PathVariable("tenant") String tenant, SearchQuery searchQuery, @RequestParam Map<String, String> filters) throws Exception {
		return internalSearch(tenant, searchQuery, filters, null);
	}

	@PostMapping("/search/arranged/{tenant}")
	@Override
	public SearchResult arrangedSearch(@PathVariable("tenant") String tenant, @RequestBody ArrangedSearchQuery searchQuery) throws Exception {
		return internalSearch(tenant, searchQuery, searchQuery.filters, searchQuery.arrangedProductSets);
	}

	private SearchResult internalSearch(String tenant, SearchQuery searchQuery, Map<String, String> filters, ProductSet[] heroProducts) throws Exception {
		MDC.put("tenant", tenant);
		try {
			// deny access to tenants that were considered invalid before
			// this is done until the latestTenantsCache invalidates that
			checkTenant(tenant);

			long start = System.currentTimeMillis();
			Map<String, Object> searchMetaData = new HashMap<>();
			try {
				SearchContext searchContext = searchContexts.computeIfAbsent(tenant, searchContextLoader::loadContext);

				final InternalSearchParams parameters = extractInternalParams(searchQuery, filters, searchContext);

				if (parameters.trace.isSet(TraceFlag.Request)) {
					log.info("called search for tenant={} on index{} through method={} with searchQuery={}, filters={} and productSet={}",
							tenant, searchContext.getConfig().getIndexName(), Thread.currentThread().getStackTrace()[2].getMethodName(), searchQuery, filters, heroProducts);
				}

				final Searcher searcher = searchClientCache.get(tenant, () -> initializeSearcher(searchContext));
				
				if (heroProducts != null) {
					parameters.heroProductSets = searchContext.heroProductHandler.resolve(heroProducts, searcher, searchContext);
				}

				SearchResult result = searcher.find(parameters, searchMetaData);

				triggerFlushIfNecessary(tenant, result);

				result.tookInMillis = System.currentTimeMillis() - start;
				return result;
			}
			catch (ElasticsearchStatusException esx) {
				if (!searchMetaData.isEmpty()) {
					log.debug("meta data collected before exception occurred: {}", searchMetaData);
				}

				handleUnavailableIndex(tenant, esx);

				// TODO: in case an index was requested where it fails because
				// fields are missing (so the application field configuration is
				// not
				// in sync with the fields indexed into ES)
				// => try to re-build the configuration by validating the fields
				// against ES _mapping endpoint

				throw esx;
			}
		}
		finally {
			MDC.remove("tenant");
		}
	}

	private void triggerFlushIfNecessary(String tenant, SearchResult result) {
		if (result.getSlices().size() > 0 && result.getSlices().get(0).hits.size() > 0) {
			String indexName = result.getSlices().get(0).hits.get(0).index;
			String prevIndexName = actualIndexPerTenant.put(tenant, indexName);
			if (prevIndexName != null && !indexName.equals(prevIndexName)) {
				log.info("flushing config for tenant {} because actual index changed from {} to {}", prevIndexName, indexName);
				CompletableFuture.runAsync(() -> flushConfig(tenant));
			}
		}
	}

	private void checkTenant(String tenant) throws Exception {
		Exception latestTenantEx = brokenTenantsCache.getIfPresent(tenant);
		if (latestTenantEx != null) {
			throw latestTenantEx;
		}
	}

	private void handleUnavailableIndex(String tenant, ElasticsearchStatusException esx) throws NotFoundException {
		if (esx.getMessage().contains("type=index_not_found_exception")) {
			// don't keep objects for invalid tenants
			SearchContext removedContext = searchContexts.remove(tenant);
			searchClientCache.invalidate(tenant);

			String indexName = removedContext != null ? removedContext.config.getIndexName() : tenant;
			NotFoundException notFoundException = new NotFoundException("Index " + indexName);

			// and deny further requests for the next N minutes
			brokenTenantsCache.put(tenant, notFoundException);

			throw notFoundException;
		}
	}

	@GetMapping("/doc/{tenant}/{id}")
	@Override
	public Document getDocument(@PathVariable("tenant") String tenant, @PathVariable("id") String docId) throws Exception {
		MDC.put("tenant", tenant);
		Document foundDoc = null;
		checkTenant(tenant);
		try {
			SearchContext searchContext = searchContexts.computeIfAbsent(tenant, searchContextLoader::loadContext);
			GetRequest getRequest = new GetRequest(searchContext.getConfig().getIndexName(), docId);
			GetResponse getResponse = esBuilder.getRestHLClient().get(getRequest, RequestOptions.DEFAULT);
			if (getResponse.isExists()) {
				foundDoc = ResultMapper.mapToOriginalDocument(getResponse.getId(), getResponse.getSource(), searchContext.fieldConfigIndex);
			}
		}
		catch (ElasticsearchStatusException esx) {
			handleUnavailableIndex(tenant, esx);
		}
		finally {
			MDC.remove("tenant");
		}
		if (foundDoc == null) {
			throw new NotFoundException("Document " + docId);
		}
		return foundDoc;
	}

	@SuppressWarnings("deprecation")
	@GetMapping("/tenants")
	@Override
	public String[] getTenants() {
		Set<String> tenants = new HashSet<>();
		try {
			esBuilder.getRestHLClient().indices()
					.getAlias(new GetAliasesRequest().indices("ocs-*"), RequestOptions.DEFAULT)
					.getAliases().values().stream()
					.filter(aliases -> aliases.size() > 0)
					.map(aliases -> aliases.iterator().next().alias())
					.forEach(tenants::add);
			;
		}
		catch (IOException e) {
			log.warn("could not retrieve ES indices", e);
		}
		tenants.addAll(searchContexts.keySet());
		tenants.addAll(plugins.getConfigurationProvider().getConfiguredTenants());
		return tenants.toArray(new String[tenants.size()]);
	}

	private Searcher initializeSearcher(SearchContext searchContext) {
		return new Searcher(esBuilder.getRestHLClient(), searchContext, registry, plugins);
	}

	@ExceptionHandler({ NotFoundException.class })
	public ResponseEntity<ExceptionResponse> handleNotFoundException(NotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ExceptionResponse.builder()
						.message(e.getMessage())
						.code(HttpStatus.NOT_FOUND.value())
						.build());
	}

	@ExceptionHandler({ IllegalArgumentException.class })
	public ResponseEntity<ExceptionResponse> handleParameterErrors(NotFoundException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ExceptionResponse.builder()
						.message(e.getMessage())
						.code(HttpStatus.BAD_REQUEST.value())
						.build());
	}

	@ExceptionHandler({ ElasticsearchStatusException.class, ExecutionException.class, IOException.class, UncheckedIOException.class, RuntimeException.class,
			ClassNotFoundException.class })
	public ResponseEntity<ExceptionResponse> handleInternalErrors(final HttpServletRequest request, Exception e) {
		final String errorId = UUID.randomUUID().toString();
		log.error("Internal Server Error {} for {}", errorId, request, e);

		ExceptionResponse.ExceptionResponseBuilder exceptionResponseBuilder = ExceptionResponse.builder()
				.message("Internal Error")
				.code(HttpStatus.INTERNAL_SERVER_ERROR.value())
				.errorId(errorId);

		if (request.getParameter("trace") != null) {
			exceptionResponseBuilder.exception(e);
		}

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(exceptionResponseBuilder.build());
	}

}
