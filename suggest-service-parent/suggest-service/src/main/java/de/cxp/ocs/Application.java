package de.cxp.ocs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Optional;

import org.rapidoid.http.HttpHeaders;
import org.rapidoid.http.Req;
import org.rapidoid.http.ReqHandler;
import org.rapidoid.setup.On;

import de.cxp.ocs.api.SuggestService;
import de.cxp.ocs.smartsuggest.QuerySuggestManager;
import de.cxp.ocs.smartsuggest.QuerySuggestManager.QuerySuggestManagerBuilder;
import de.cxp.ocs.smartsuggest.limiter.ConfigurableShareLimiter;
import de.cxp.ocs.smartsuggest.limiter.GroupedCutOffLimiter;
import de.cxp.ocs.smartsuggest.limiter.Limiter;
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
		final SuggestService suggestService = new SuggestServiceImpl(querySuggestManager, properties);

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
							String userQuery = req.params().get("userQuery");
							if (userQuery != null && !userQuery.isEmpty()) {
								String indexname = req.param("indexname");
								String filter = req.param("filter", null);
								int limit = Integer.valueOf(req.param("limit", "10"));

								return suggestService.suggest(indexname, userQuery, limit, filter);
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

		String mgmPathPrefix = properties.getManagementPathPrefix();
		On.get(mgmPathPrefix + "/metrics").plain(() -> meterRegistry.scrape().getBytes());
		On.get(mgmPathPrefix + "/prometheus").plain(() -> meterRegistry.scrape().getBytes());
		On.get(mgmPathPrefix + "/health").plain("up");
	}

	public static QuerySuggestManager getQuerySuggestManager(SuggestProperties props, MeterRegistry meterRegistry) {
		QuerySuggestManagerBuilder querySuggestManagerBuilder = QuerySuggestManager.builder()
				.indexFolder(props.getIndexFolder())
				.updateRate(props.getUpdateRateInSeconds())
				.addMetricsRegistryAdapter(MeterRegistryAdapter.of(meterRegistry))
				.preloadIndexes(props.getPreloadIndexes());

		final Optional<String> groupKey = props.getGroupKey();
		if (groupKey.isPresent()) {
			Optional<String[]> groupDeduplicationOrder = props.getGroupDeduplicationOrder();
			Limiter limiter = props.getGroupedShareConf()
					.map(conf -> (Limiter) new ConfigurableShareLimiter(groupKey.get(), conf, groupDeduplicationOrder))
					.orElseGet(() -> {
						Integer cutoffDefault = props.getGroupedCutoffDefaultSize();
						LinkedHashMap<String, Integer> conf = props.getGroupedCutoffConf().orElse(new LinkedHashMap<>(0));
						return new GroupedCutOffLimiter(groupKey.get(), cutoffDefault, conf, groupDeduplicationOrder);
					});
			querySuggestManagerBuilder.withLimiter(limiter);
		}

		return querySuggestManagerBuilder.build();
	}

}
