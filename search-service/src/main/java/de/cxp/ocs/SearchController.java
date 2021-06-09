package de.cxp.ocs;

import static de.cxp.ocs.util.SearchParamsParser.extractInternalParams;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import de.cxp.ocs.api.searcher.SearchService;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldConfiguration;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.SearchConfiguration;
import de.cxp.ocs.elasticsearch.ElasticSearchBuilder;
import de.cxp.ocs.elasticsearch.FieldConfigFetcher;
import de.cxp.ocs.elasticsearch.ResultMapper;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.elasticsearch.prodset.DynamicProductSetResolver;
import de.cxp.ocs.elasticsearch.prodset.ProductSetResolver;
import de.cxp.ocs.elasticsearch.prodset.ProductSetResolverUtil;
import de.cxp.ocs.elasticsearch.prodset.StaticProductSetResolver;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.params.ArrangedSearchQuery;
import de.cxp.ocs.model.params.DynamicProductSet;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.params.StaticProductSet;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.spi.search.UserQueryPreprocessor;
import de.cxp.ocs.util.InternalSearchParams;
import de.cxp.ocs.util.NotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@RefreshScope
@CrossOrigin(origins = "http://localhost:8081")
@RestController
@RequestMapping(path = "/search-api/v1")
@Slf4j
public class SearchController implements SearchService {

	@Autowired
	@NonNull
	private ElasticSearchBuilder esBuilder;

	@Autowired
	@NonNull
	private SearchPlugins plugins;

	@Autowired
	private MeterRegistry registry;

	private ProductSetResolverUtil productSetResolverUtil;

	private final Map<String, SearchContext> searchContexts = new ConcurrentHashMap<>();

	private final Map<String, String> actualIndexPerTenant = new ConcurrentHashMap<>();

	private final Cache<String, Searcher> searchClientCache = CacheBuilder.newBuilder()
			.expireAfterAccess(10, TimeUnit.MINUTES)
			.maximumSize(10)
			.build();

	private final Cache<String, Exception> brokenTenantsCache = CacheBuilder.newBuilder()
			.expireAfterWrite(5, TimeUnit.MINUTES)
			.build();

	@PostConstruct
	public void init() {
		Map<String, ProductSetResolver> resolvers = new HashMap<>(2);
		resolvers.put(new DynamicProductSet().type, new DynamicProductSetResolver());
		resolvers.put(new StaticProductSet().type, new StaticProductSetResolver());
		// if the dependency to the Searcher & SearchContext could be removed,
		// the product set resolver could be part of the SPI/plug-in mechanism
		productSetResolverUtil = new ProductSetResolverUtil(resolvers);
	}

	@GetMapping("/flushConfig/{tenant}")
	public ResponseEntity<HttpStatus> flushConfig(@PathVariable("tenant") String tenant) {
		MDC.put("tenant", tenant);
		HttpStatus status;
		brokenTenantsCache.invalidate(tenant);
		SearchContext searchContext = loadContext(tenant);
		SearchContext oldConfig = searchContexts.put(tenant, searchContext);
		if (oldConfig == null) {
			log.info("config successfuly loaded for tenant {}", tenant);
			status = HttpStatus.CREATED;
		}
		else if (oldConfig.equals(searchContexts.get(tenant))) {
			log.info("config flush did not modify config for tenant {}", tenant);
			status = HttpStatus.NOT_MODIFIED;
		}
		else {
			log.info("config successfuly reloaded for tenant {}", tenant);
			status = HttpStatus.OK;
			searchClientCache.put(tenant, initializeSearcher(searchContext));
		}
		MDC.remove("tenant");

		return new ResponseEntity<>(status, status);
	}

	@GetMapping("/search/{tenant}")
	@Override
	public SearchResult search(@PathVariable("tenant") String tenant, SearchQuery searchQuery, @RequestParam Map<String, String> filters) throws Exception {
		// TODO: add plugin that may inject hero products
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
			SearchContext searchContext = searchContexts.computeIfAbsent(tenant, this::loadContext);

			final InternalSearchParams parameters = extractInternalParams(searchQuery, filters, searchContext);

			try {
				final Searcher searcher = searchClientCache.get(tenant, () -> initializeSearcher(searchContext));
				if (heroProducts != null) {
					parameters.heroProductSets = productSetResolverUtil.resolve(heroProducts, searcher, searchContext);
				}

				SearchResult result = searcher.find(parameters);

				triggerFlushIfNecessary(tenant, result);

				result.tookInMillis = System.currentTimeMillis() - start;
				return result;
			}
			catch (ElasticsearchStatusException esx) {
				handleUnavailableIndex(tenant, searchContext, esx);

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

	private void handleUnavailableIndex(String tenant, SearchContext searchContext, ElasticsearchStatusException esx) throws NotFoundException {
		if (esx.getMessage().contains("type=index_not_found_exception")) {
			// don't keep objects for invalid tenants
			searchContexts.remove(tenant);
			searchClientCache.invalidate(tenant);
			// and deny further requests for the next N minutes
			brokenTenantsCache.put(tenant, esx);
			throw new NotFoundException("Index " + searchContext.config.getIndexName());
		}
	}

	@GetMapping("/doc/{tenant}/{id}")
	@Override
	public Document getDocument(@PathVariable("tenant") String tenant, @PathVariable("id") String docId) throws Exception {
		MDC.put("tenant", tenant);
		Document foundDoc = null;
		try {
			SearchContext searchContext = searchContexts.computeIfAbsent(tenant, this::loadContext);
			GetRequest getRequest = new GetRequest(searchContext.getConfig().getIndexName(), docId);
			GetResponse getResponse = esBuilder.getRestHLClient().get(getRequest, RequestOptions.DEFAULT);
			if (getResponse.isExists()) {
				foundDoc = ResultMapper.mapToOriginalDocument(getResponse.getId(), getResponse.getSource(), searchContext.fieldConfigIndex);

				Object resultData = getResponse.getSource().get(FieldConstants.RESULT_DATA);
				if (resultData != null && resultData instanceof Map) {
					foundDoc = new Document(getResponse.getId());
					foundDoc.setData((Map<String, Object>) resultData);
				}
			}
		}
		finally {
			MDC.remove("tenant");
		}
		if (foundDoc == null) {
			throw new NotFoundException("Document " + docId);
		}
		return foundDoc;
	}

	@GetMapping("/tenants")
	@Override
	public String[] getTenants() {
		Set<String> tenants = new HashSet<>();
		try {
			esBuilder.getRestHLClient().indices()
					.getAlias(new GetAliasesRequest(), RequestOptions.DEFAULT)
					.getAliases()
					.entrySet()
					.stream()
					.filter(aliasEntry -> !aliasEntry.getKey().startsWith(".") && !aliasEntry.getValue().isEmpty())
					.map(aliasEntry -> aliasEntry.getValue().iterator().next().alias())
					.forEach(tenants::add);
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

	private SearchContext loadContext(String tenant) {
		SearchConfiguration searchConfig = plugins.getConfigurationProvider().getTenantSearchConfiguration(tenant);
		FieldConfigIndex fieldConfigAccess = loadFieldConfiguration(searchConfig.getIndexName());
		List<UserQueryPreprocessor> userQueryPreprocessors = SearchPlugins.initialize(
				searchConfig.getQueryProcessing().getUserQueryPreprocessors(),
				plugins.getUserQueryPreprocessors(),
				searchConfig.getPluginConfiguration());
		log.debug("Using index {} for tenant {}", searchConfig.getIndexName(), tenant);
		return new SearchContext(fieldConfigAccess, searchConfig, userQueryPreprocessors);
	}

	private FieldConfigIndex loadFieldConfiguration(String indexName) {
		FieldConfiguration fieldConfig;
		try {
			fieldConfig = new FieldConfigFetcher(esBuilder.getRestHLClient()).fetchConfig(indexName);
		}
		catch (IOException e) {
			log.error("couldn't fetch field configuration from index {}", indexName);
			throw new UncheckedIOException(e);
		}
		return new FieldConfigIndex(fieldConfig);
	}

	@ExceptionHandler({ NotFoundException.class })
	public ResponseEntity<ExceptionResponse> handleNotFoundException(NotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ExceptionResponse.builder()
						.message(e.getMessage())
						.code(HttpStatus.NOT_FOUND.value())
						.build());
	}

	@ExceptionHandler({ ElasticsearchStatusException.class, ExecutionException.class, IOException.class, UncheckedIOException.class, RuntimeException.class,
			ClassNotFoundException.class })
	public ResponseEntity<ExceptionResponse> handleInternalErrors(final HttpServletRequest request, Exception e) {
		final String errorId = UUID.randomUUID().toString();
		log.error("Internal Server Error {} for {}", errorId, request, e);

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ExceptionResponse.builder()
						.message("Internal Error")
						.code(HttpStatus.INTERNAL_SERVER_ERROR.value())
						.errorId(errorId)
						.build());
	}

}
