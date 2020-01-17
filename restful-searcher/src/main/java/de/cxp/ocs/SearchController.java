package de.cxp.ocs;

import static de.cxp.ocs.util.SearchParamsParser.parseParams;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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

import de.cxp.ocs.config.ApplicationProperties;
import de.cxp.ocs.config.IndexConfiguration;
import de.cxp.ocs.config.SearchConfiguration;
import de.cxp.ocs.config.TenantSearchConfiguration;
import de.cxp.ocs.elasticsearch.ElasticSearchBuilder;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.util.InternalSearchParams;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@RefreshScope
@CrossOrigin(origins = "http://localhost:8081")
@RestController
@RequestMapping(path = "/search-api/v1")
@Slf4j
public class SearchController {

	@Autowired
	@NonNull
	private ElasticSearchBuilder esBuilder;

	@Autowired
	@NonNull
	private ApplicationProperties properties;

	@Autowired
	private MeterRegistry registry;

	private final Map<String, SearchConfiguration> searchConfigs = new HashMap<>();

	private final Cache<String, Searcher> searchClientCache = CacheBuilder.newBuilder()
			.expireAfterAccess(10, TimeUnit.MINUTES)
			.maximumSize(10)
			.build();

	@GetMapping("/{tenant}")
	public SearchResult get(
			@PathVariable("tenant") String tenant,
			@RequestParam("q") String query,
			@RequestParam(required = false, name = "searchhub") boolean searchhub,
			@RequestParam(required = false) Map<String, Object> params) throws ExecutionException, IOException {
		SearchConfiguration searchConfig = searchConfigs.computeIfAbsent(tenant, this::getConfigForTenant);
		log.debug("Using index {} for tenant {}", searchConfig.getIndexName(), tenant);

		final InternalSearchParams parameters = parseParams(params, searchConfig.getFieldConfiguration().getFields());

		final Searcher searcher = searchClientCache.get(tenant, () -> new Searcher(esBuilder.getRestHLClient(), searchConfig, registry));
		SearchResult result = searcher.find(query, parameters);
		result.setSearchQuery(query);

		return result;
	}

	private SearchConfiguration getConfigForTenant(String tenant) {
		SearchConfiguration configCopy = new SearchConfiguration();

		TenantSearchConfiguration tenantSearchConf = getTenantSearchConfiguration(tenant);

		IndexConfiguration indexConfig = getIndexConfiguration(tenantSearchConf.getIndexName());

		configCopy.setIndexName(tenantSearchConf.getIndexName());
		configCopy.setFacetConfiguration(tenantSearchConf.getFacetConfiguration());
		configCopy.setFieldConfiguration(indexConfig.getFieldConfiguration());
		configCopy.getQueryConfigs().putAll(tenantSearchConf.getQueryConfiguration());
		configCopy.setScoring(tenantSearchConf.getScoringConfiguration());
		return configCopy;
	}

	private TenantSearchConfiguration getTenantSearchConfiguration(String tenant) {
		return properties.getTenantConfig().getOrDefault(tenant, properties.getDefaultTenantConfig());
	}

	private IndexConfiguration getIndexConfiguration(String indexName) {
		return properties.getIndexConfig().getOrDefault(indexName, properties.getDefaultIndexConfig());
	}

	@ExceptionHandler(
			value = { ExecutionException.class, IOException.class, RuntimeException.class,
					ClassNotFoundException.class })
	public ResponseEntity<String> handleInternalErrors(Exception e) {
		final String errorId = UUID.randomUUID().toString();
		log.error("Internal Server Error " + errorId, e);
		return new ResponseEntity<>("Something went wrong. Error reference: " + errorId,
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

}
