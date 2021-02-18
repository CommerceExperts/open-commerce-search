package de.cxp.ocs.smartsuggest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import de.cxp.ocs.smartsuggest.querysuggester.*;
import de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneSuggesterFactory;
import de.cxp.ocs.smartsuggest.spi.SuggestDataProvider;
import de.cxp.ocs.smartsuggest.updater.SuggestionsUpdater;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * The {@link QuerySuggestManager} cares about the creation of
 * {@link QuerySuggester} objects and
 * also makes sure they are coupled to an internal asynchronous update process
 * which is scheduled according a configurable update rate.
 * </p>
 * <p>
 * Since the {@link QuerySuggestManager} internally holds an executor service,
 * it must be
 * closed when it and the created QuerySuggester instances are no longer in use.
 * </p>
 */
@Slf4j
public class QuerySuggestManager implements AutoCloseable {

	public static final String DEBUG_PROPERTY = "ocs.smartsuggest.debug";

	private final SuggestDataProvider suggestDataProvider;

	private final Map<String, QuerySuggester> activeQuerySuggesters = new ConcurrentHashMap<>();

	// @formatter:off
	private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
		
		@Override
		public Thread newThread(Runnable r) {
			Thread thread = new Thread(r, "QuerySuggestUpdater-Thread");
			thread.setDaemon(true);
			return thread;
		}
	});
	// @formatter:on

	private final Map<String, ScheduledFuture<?>> scheduledTasks = new HashMap<>();

	private Path suggestIndexFolder;

	private long updateRate = 60;

	private MeterRegistryAdapter metricsRegistry;

	@Deprecated
	private SuggesterEngine engine = SuggesterEngine.LUCENE;

	public static class QuerySuggestManagerBuilder {

		private Path suggestIndexFolder;

		private int updateRate = 60;

		private SuggesterEngine engine = SuggesterEngine.LUCENE;

		private Set<String> preloadIndexes = new HashSet<>();

		private MeterRegistryAdapter metricsRegistry;

		/**
		 * Sets the root path where the indices for the different tenants
		 * will be stored. Required for LUCENE engine.
		 *
		 * @param indexFolder
		 *        the root path where the indices for the different tenants
		 *        will be stored.
		 * @return the changed builder
		 */
		public QuerySuggestManagerBuilder indexFolder(Path indexFolder) {
			this.suggestIndexFolder = indexFolder;
			return this;
		}

		/**
		 * Set the rate (in seconds) at which the update should run.
		 * The value must be 5 &lt;= x &lt;= 3600.
		 * Default: 60
		 * 
		 * @param seconds
		 *        positive integer
		 * @return the changed builder
		 */
		public QuerySuggestManagerBuilder updateRate(int seconds) {
			if (seconds > 3600) seconds = 3600;
			else if (seconds < 5) seconds = 5;
			updateRate = seconds;
			return this;
		}

		/**
		 * Changes the engine that should be used generate the suggestions.
		 * Per default {@code SuggesterEngine::LUCENE} is used.
		 * 
		 * @param engine
		 *        engine to use
		 * @return the changed builder
		 */
		public QuerySuggestManagerBuilder engine(SuggesterEngine engine) {
			this.engine = engine;
			return this;
		}

		/**
		 * specify indexes that should be loaded immediately after
		 * initialization.
		 * 
		 * @param indexNames
		 *        list of index names to be initialized synchronously when
		 *        calling 'build()'
		 * @return
		 *         the changed builder
		 */
		public QuerySuggestManagerBuilder preloadIndexes(String... indexNames) {
			for (String indexName : indexNames) {
				preloadIndexes.add(indexName);
			}
			return this;
		}

		/**
		 * Optionally add micrometer.io MeterRegistry. An internal adapter is
		 * used in order to avoid ClassNotFound exception in case Micrometer is
		 * not on the classpath.
		 * 
		 * @param reg
		 *        adapter with the wanted meter registry
		 * @return the changed builder
		 */
		public QuerySuggestManagerBuilder addMetricsRegistryAdapter(MeterRegistryAdapter reg) {
			this.metricsRegistry = reg;
			return this;
		}

		public QuerySuggestManager build() {
			if (SuggesterEngine.LUCENE.equals(engine) && suggestIndexFolder == null) {
				throw new IllegalArgumentException("required 'indexFolder' not specified");
			}
			QuerySuggestManager querySuggestManager = new QuerySuggestManager();
			querySuggestManager.suggestIndexFolder = suggestIndexFolder;
			querySuggestManager.updateRate = updateRate;
			querySuggestManager.metricsRegistry = metricsRegistry;
			querySuggestManager.engine = engine;
			if (preloadIndexes.size() > 0) {
				List<CompletableFuture<QuerySuggester>> futures = preloadIndexes.stream()
						.map(indexName -> CompletableFuture.supplyAsync(() -> querySuggestManager.getQuerySuggester(indexName, true)))
						.collect(Collectors.toList());
				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
			}
			return querySuggestManager;
		}
	}

	public static QuerySuggestManagerBuilder builder() {
		return new QuerySuggestManagerBuilder();
	}

	/**
	 * Basic constructor to create QuerySuggestManager with default settings.
	 * To customize behavioral settings, use the builder instead.
	 */
	private QuerySuggestManager() {
		ServiceLoader<SuggestDataProvider> serviceLoader = ServiceLoader.load(SuggestDataProvider.class);
		Iterator<SuggestDataProvider> suggestDataProviders = serviceLoader.iterator();
		SuggestDataProvider dataProvider = null;
		if (suggestDataProviders.hasNext()) {
			dataProvider = suggestDataProviders.next();
		}
		else {
			throw new IllegalStateException("No SuggestDataProvider found on classpath! Suggest unusable that way."
					+ " Please provide a SuggestDataProvider implementation accessible via ServiceLoader.");
		}
		if (suggestDataProviders.hasNext()) {
			// TODO build a "CompoundSuggestDataServiceProvider" that checks all
			// available service providers for data
			log.warn("more than one SuggestDataServiceProvider found! Will only use the first one of type {}", dataProvider.getClass().getCanonicalName());
		}
		log.info("initialized SmartSuggest with {}", dataProvider.getClass().getCanonicalName());
		suggestDataProvider = dataProvider;
	}

	/**
	 * internal constructor for testing
	 * 
	 * @param dataProvider
	 */
	QuerySuggestManager(SuggestDataProvider dataProvider) {
		suggestDataProvider = dataProvider;
		try {
			suggestIndexFolder = Files.createTempDirectory(QuerySuggestManager.class.getSimpleName() + "-for-testing-");
		}
		catch (IOException iox) {
			throw new UncheckedIOException(iox);
		}
	}

	/**
	 * Set the rate (in seconds) at which the update should run.
	 * The value must be 5 &lt;= x &lt;= 3600.
	 *
	 * That rate is only applied to QuerySuggesters that will be fetched from
	 * the
	 * time after this value is set.
	 * 
	 * @deprecated use builder instead!
	 * @param seconds
	 */
	@Deprecated
	public void setUpdateRate(int seconds) {
		if (seconds > 3600) seconds = 3600;
		else if (seconds < 5) seconds = 5;
		updateRate = seconds;
	}

	/**
	 * Retrieves the query suggester for the given indexName. Initializes a new
	 * query suggester if non exists yet, for that client.
	 * A background job ensures the data of that query suggester
	 * get's updated regularly.
	 * 
	 * @param indexName
	 *        index name of the wanted suggester
	 * @return
	 *         initialized query suggester
	 */
	public QuerySuggester getQuerySuggester(@NonNull String indexName) {
		return getQuerySuggester(indexName, false);
	}

	public QuerySuggester getQuerySuggester(@NonNull String indexName, boolean synchronous) {
		ScheduledFuture<?> scheduledFuture = scheduledTasks.get(indexName);
		if (scheduledFuture != null && scheduledFuture.isDone()) {
			scheduledTasks.remove(indexName);
			activeQuerySuggesters.remove(indexName);
		}
		return activeQuerySuggesters.computeIfAbsent(indexName, (_tenant) -> initializeQuerySuggester(_tenant, synchronous));
	}

	public void destroyQuerySuggester(String indexName) {
		ScheduledFuture<?> scheduledFuture = scheduledTasks.get(indexName);
		if (scheduledFuture != null && !scheduledFuture.isDone()) {
			scheduledFuture.cancel(true);
		}
		QuerySuggester removedSuggester = activeQuerySuggesters.remove(indexName);
		if (removedSuggester != null) {
			removedSuggester.destroy();	
		}
	}

	private QuerySuggester initializeQuerySuggester(String indexName, boolean synchronous) {
		QuerySuggesterProxy updateableQuerySuggester = new QuerySuggesterProxy(indexName);

		if (!"noop".equals(indexName)) {
			Path tenantFolder = suggestIndexFolder.resolve(indexName.toString());
			SuggesterFactory factory = new LuceneSuggesterFactory(tenantFolder);
			SuggestionsUpdater updateTask = new SuggestionsUpdater(suggestDataProvider, indexName, updateableQuerySuggester, factory);
			updateTask.setMetricsRegistryAdapter(Optional.ofNullable(metricsRegistry));

			long initialDelay = 0;
			if (synchronous) {
				initialDelay = updateRate;
				updateTask.run();
			}
			ScheduledFuture<?> scheduledTask = executor.scheduleWithFixedDelay(updateTask, initialDelay, updateRate, TimeUnit.SECONDS);
			scheduledTasks.put(indexName, scheduledTask);

			log.info("Successfully initialized QuerySuggester for indexName {}", indexName);
		}

		return updateableQuerySuggester;
	}

	@Override
	public void close() {
		scheduledTasks.values().forEach(t -> t.cancel(true));
		executor.shutdown();
	}

}