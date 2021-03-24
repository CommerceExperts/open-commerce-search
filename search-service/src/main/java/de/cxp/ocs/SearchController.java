package de.cxp.ocs;

import static de.cxp.ocs.util.SearchParamsParser.parseFilters;
import static de.cxp.ocs.util.SearchParamsParser.parseSortings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.client.RequestOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import de.cxp.ocs.api.searcher.SearchService;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldConfiguration;
import de.cxp.ocs.config.InternalSearchConfiguration;
import de.cxp.ocs.config.SearchConfiguration;
import de.cxp.ocs.elasticsearch.ElasticSearchBuilder;
import de.cxp.ocs.elasticsearch.FieldConfigFetcher;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.spi.search.SearchConfigurationProvider;
import de.cxp.ocs.util.InternalSearchParams;
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
	private SearchConfigurationProvider searchConfigProvider;

	@Autowired
	private MeterRegistry registry;


	private final Map<String, InternalSearchConfiguration> searchConfigs = new HashMap<>();

	private final Cache<String, Searcher> searchClientCache = CacheBuilder.newBuilder()
			.expireAfterAccess(10, TimeUnit.MINUTES)
			.maximumSize(10)
			.build();

	private final Cache<String, Exception> brokenTenantsCache = CacheBuilder.newBuilder()
			.expireAfterWrite(5, TimeUnit.MINUTES)
			.build();

	@GetMapping("/flushConfig/{tenant}")
	public ResponseEntity<HttpStatus> flushConfig(@PathVariable("tenant") String tenant) {
		HttpStatus status;
		brokenTenantsCache.invalidate(tenant);
		InternalSearchConfiguration oldConfig = searchConfigs.put(tenant, getConfigForTenant(tenant));
		if (oldConfig == null) {
			status = HttpStatus.CREATED;
		}
		else if (oldConfig.equals(searchConfigs.get(tenant))) {
			status = HttpStatus.NOT_MODIFIED;
		}
		else {
			status = HttpStatus.OK;
			searchClientCache.invalidate(tenant);
		}

		return new ResponseEntity<>(status, status);
	}

	@GetMapping("/search/{tenant}")
	@Override
	public SearchResult search(@PathVariable("tenant") String tenant, SearchQuery searchQuery, @RequestParam Map<String, String> filters) throws Exception {
		// deny access to tenants that were considered invalid before
		// this is done until the latestTenantsCache invalidates that
		Exception latestTenantEx = brokenTenantsCache.getIfPresent(tenant);
		if (latestTenantEx != null) {
			throw latestTenantEx;
		}

		InternalSearchConfiguration searchConfig = searchConfigs.computeIfAbsent(tenant, this::getConfigForTenant);
		log.debug("Using index {} for tenant {}", searchConfig.provided.getIndexName(), tenant);

		final InternalSearchParams parameters = new InternalSearchParams();
		parameters.userQuery = searchQuery.q;
		parameters.limit = searchQuery.limit;
		parameters.offset = searchQuery.offset;
		parameters.withFacets = searchQuery.withFacets;
		if (searchQuery.sort != null) {
			parameters.sortings = parseSortings(searchQuery.sort, searchConfig.getFieldConfigIndex());
		}
		parameters.filters = parseFilters(filters, searchConfig.getFieldConfigIndex());

		try {
			final Searcher searcher = searchClientCache.get(tenant, () -> new Searcher(esBuilder.getRestHLClient(), searchConfig, registry));
			return searcher.find(parameters);
		}
		catch (ElasticsearchStatusException esx) {
			// TODO: in case an index was requested where it fails because
			// fields are missing (so the application field configuration is not
			// in sync with the fields indexed into ES)
			// => try to re-build the configuration by validating the fields
			// against ES _mapping endpoint
			if (esx.getMessage().contains("type=index_not_found_exception")) {
				// don't keep objects for invalid tenants
				searchConfigs.remove(tenant);
				searchClientCache.invalidate(tenant);
				// and deny further requests for the next N minutes
				brokenTenantsCache.put(tenant, esx);
			}
			throw esx;
		}
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
		tenants.addAll(searchConfigs.keySet());
		tenants.addAll(searchConfigProvider.getConfiguredTenants());
		return tenants.toArray(new String[tenants.size()]);
	}

	private InternalSearchConfiguration getConfigForTenant(String tenant) {
		SearchConfiguration searchConfig = searchConfigProvider.getTenantSearchConfiguration(tenant);
		
		FieldConfiguration fieldConfig;
		try {
			fieldConfig = new FieldConfigFetcher(esBuilder.getRestHLClient()).fetchConfig(searchConfig.getIndexName());
		}
		catch (IOException e) {
			log.error("couldn't fetch field configuration from index {}", searchConfig.getIndexName());
			throw new UncheckedIOException(e);
		}
		return new InternalSearchConfiguration(new FieldConfigIndex(fieldConfig), searchConfig);
	}

	@ExceptionHandler({ ElasticsearchStatusException.class })
	public ResponseEntity<ExceptionResponse> handleElasticsearchExceptions(ElasticsearchStatusException e) {
		if (e.getMessage().contains("type=index_not_found_exception")) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(ExceptionResponse.builder()
							.message(e.getMessage())
							.code(HttpStatus.NOT_FOUND.value())
							.build());
		}

		return handleInternalErrors(e);
	}

	@ExceptionHandler({ ExecutionException.class, IOException.class, UncheckedIOException.class, RuntimeException.class, ClassNotFoundException.class })
	public ResponseEntity<ExceptionResponse> handleInternalErrors(Exception e) {
		final String errorId = UUID.randomUUID().toString();
		log.error("Internal Server Error " + errorId, e);

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ExceptionResponse.builder()
						.message("Internal Error")
						.code(HttpStatus.INTERNAL_SERVER_ERROR.value())
						.errorId(errorId)
						.build());
	}

}
