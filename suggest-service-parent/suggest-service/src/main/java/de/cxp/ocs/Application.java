package de.cxp.ocs;

import java.util.*;

import org.rapidoid.http.*;
import org.rapidoid.setup.On;

import de.cxp.ocs.api.SuggestService;
import de.cxp.ocs.smartsuggest.QuerySuggestManager;
import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application {

	protected static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";

	public static void main(String[] args) {
		log.info("starting suggest-service");
		SuggestProperties properties = new SuggestProperties();
		final PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
		final QuerySuggestManager querySuggestManager = getQuerySuggestManager(properties, meterRegistry);
		final SuggestService suggestService = new SuggestServiceImpl(querySuggestManager);

		On.port(properties.getServerPort()).address(properties.getServerAdress());

		On.get("/suggest-api/v1/{indexname}/suggest")
				.managed(false)
				.json(new ReqHandler() {

					private static final long serialVersionUID = 1L;

					@Override
					public Object execute(Req req) throws Exception {
						req.response().header(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
						req.response().header(HttpHeaders.CACHE_CONTROL.name(), "public, max-age=60");
						try {
							Map<String, String> filters = new HashMap<>(req.params());
							String userQuery = filters.remove("userQuery");
							String indexname = filters.remove("indexname");
							if (userQuery != null && !userQuery.isEmpty()) {
								int limit = Integer.valueOf(req.param("limit", "10"));
								filters.remove("indexname");
								return suggestService.suggest(indexname, userQuery, limit, filters);
							}
							else {
								req.response().code(400).header("Warning", "no userQuery defined");
							}
						}
						catch (Exception e) {
							log.error("error for request with parameters '{}': {}: {}",
									req.params(), e.getClass().getSimpleName(), e.getMessage());
							req.response().code(500);
						}
						return Collections.emptyList();
					}
				});

		On.get("/metrics").plain(() -> meterRegistry.scrape().getBytes());
		On.get("/prometheus").plain(() -> meterRegistry.scrape().getBytes());
		On.get("/health").plain("up");
	}

	public static QuerySuggestManager getQuerySuggestManager(SuggestProperties props, MeterRegistry meterRegistry) {
		return QuerySuggestManager.builder()
				.indexFolder(props.getIndexFolder())
				.updateRate(props.getUpdateRateInSeconds())
				.addMetricsRegistryAdapter(MeterRegistryAdapter.of(meterRegistry))
				.preloadIndexes(props.getPreloadIndexes())
				.build();
	}

}
