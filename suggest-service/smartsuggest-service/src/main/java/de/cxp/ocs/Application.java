package de.cxp.ocs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;

import org.rapidoid.http.HttpHeaders;
import org.rapidoid.http.Req;
import org.rapidoid.http.ReqHandler;
import org.rapidoid.setup.On;

import de.cxp.ocs.config.SmartSuggestProperties;
import de.cxp.ocs.service.SmartSuggestService;
import de.cxp.ocs.smartsuggest.QuerySuggestManager;
import de.cxp.ocs.smartsuggest.QuerySuggestManager.QuerySuggestManagerBuilder;
import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application {

	protected static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";

	public static void main(String[] args) {
		log.info("starting smartsuggest-service");
		final SmartSuggestProperties props = new SmartSuggestProperties();

		final QuerySuggestManager querySuggestManager = getQuerySuggestManager(
				props,
				Optional.ofNullable(System.getenv("INIT_INDEXES")),
				Optional.ofNullable(System.getenv("INDEX_FOLDER")),
				Optional.empty());
		final SmartSuggestService suggestService = new SmartSuggestService(querySuggestManager);

		On.port(Integer.getInteger("SERVER_PORT", 8081)).address("0.0.0.0");
		
		// endpoint that returns the suggestions in match-groups
		On.get("/smartsuggest/{indexname}/detail").managed(false).json(new ReqHandler() {

			private static final long serialVersionUID = 1L;

			@Override
			public Object execute(Req req) throws Exception {
				req.response().header(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
				req.response().header(HttpHeaders.CACHE_CONTROL.name(), "public, max-age=60");
				try {
					String userQuery = req.param("userQuery");
					String indexname = req.param("indexname");
					if (userQuery != null && !userQuery.isEmpty()) {
						int limit = Integer.valueOf(req.param("limit", "10"));
						return suggestService.getSuggestions(userQuery, indexname, limit);
					}
					else {
						req.response().code(400);
					}
				}
				catch (Exception e) {
					req.response().code(500);
				}
				return Collections.emptyList();
			}
		});
		
		// simple string-list endpoint
		On.get("/smartsuggest/{indexname}").managed(false).json(new ReqHandler() {

			private static final long serialVersionUID = 2L;

			@Override
			public Object execute(Req req) throws Exception {
				req.response().header(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
				req.response().header(HttpHeaders.CACHE_CONTROL.name(), "public, max-age=60");
				try {
					String indexname = req.param("indexname");
					String userQuery = req.param("userQuery");
					int limit = Integer.valueOf(req.param("limit", "10"));
					if (userQuery != null) {
						return suggestService.getSimpleSuggestions(userQuery, indexname, limit);
					}
					else {
						req.response().code(400);
					}
				}
				catch (Exception e) {
					req.response().code(500);
				}
				return Collections.emptyList();
			}
		});
	}

	public static Path indexFolder(Optional<String> indexFolder) {
		return indexFolder
				.map(path -> Paths.get(path))
				.orElseGet(() -> {
					try {
						return Files.createTempDirectory("SmartSuggest");
					}
					catch (IOException iox) {
						throw new UncheckedIOException(iox);
					}
				});
	}

	public static QuerySuggestManager getQuerySuggestManager(
			SmartSuggestProperties props,
			Optional<String> initTenants,
			Optional<String> indexFolder,
			Optional<MeterRegistry> meterRegistry) {

		QuerySuggestManagerBuilder builder = QuerySuggestManager.builder()
				.indexFolder(indexFolder(indexFolder))
				.updateRate(props.getUpdateRateInSeconds());

		meterRegistry.ifPresent(mr -> builder.addMetricsRegistryAdapter(MeterRegistryAdapter.of(mr)));

		initTenants.ifPresent(indexStrings -> builder.preloadIndexes(indexStrings.split(",")));
		props.getPreloadTenants().forEach(builder::preloadIndexes);

		return builder.build();
	}

}
