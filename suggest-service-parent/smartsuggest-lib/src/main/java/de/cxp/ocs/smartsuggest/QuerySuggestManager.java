package de.cxp.ocs.smartsuggest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import de.cxp.ocs.smartsuggest.querysuggester.CompoundQuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.NoopQuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggesterProxy;
import de.cxp.ocs.smartsuggest.querysuggester.SuggesterEngine;
import de.cxp.ocs.smartsuggest.querysuggester.SuggesterFactory;
import de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneSuggesterFactory;
import de.cxp.ocs.smartsuggest.spi.MergingSuggestDataProvider;
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

	private final List<SuggestDataProvider> suggestDataProviders;

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

	private boolean useDataMerger = false;

	private MeterRegistryAdapter metricsRegistry;

	@Deprecated
	private SuggesterEngine engine = SuggesterEngine.LUCENE;

	public static class QuerySuggestManagerBuilder {

		private Path suggestIndexFolder;

		private int updateRate = 60;

		private boolean useDataMerger = false;

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
		 * <p>
		 * Per default for each provided data set, a single suggester is set up.
		 * If this flag is enabled, the {@link MergingSuggestDataProvider} will
		 * be used to merge all provided data for a given index.
		 * </p>
		 * <p>
		 * This approach is best suitable in these cases:
		 * </p>
		 * <ul>
		 * <li>You want one data source to control the stop-words for all data
		 * sources</li>
		 * <li>You don't need to filter on "natural tags" AND the "type
		 * tag" (only one works at a time)</li>
		 * <li>You don't need the fuzzy matches: they don't work with
		 * filtering</li>
		 * </ul>
		 * <p>
		 * Also the data providers should deliver the data with the same locale
		 * setting (otherwise only the first locale is picked and a warning is
		 * logged).
		 * </p>
		 * 
		 * @see MergingSuggestDataProvider
		 * @return builder
		 */
		public QuerySuggestManagerBuilder useDataMerger() {
			this.useDataMerger = true;
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
			querySuggestManager.useDataMerger = useDataMerger;
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
		Iterator<SuggestDataProvider> loadedSDPs = serviceLoader.iterator();
		List<SuggestDataProvider> dataProviders = new ArrayList<>();
		if (!loadedSDPs.hasNext()) {
			throw new IllegalStateException("No SuggestDataProvider found on classpath! Suggest unusable that way."
					+ " Please provide a SuggestDataProvider implementation accessible via ServiceLoader.");
		}
		while (loadedSDPs.hasNext()) {
			dataProviders.add(loadedSDPs.next());
		}
		log.info("initialized SmartSuggest with {}", dataProviders.getClass().getCanonicalName());
		
		suggestDataProviders = prepareSuggestDataProviders(dataProviders);
	}

	private List<SuggestDataProvider> prepareSuggestDataProviders(List<SuggestDataProvider> dataProviders) {
		if (useDataMerger) {
			return Collections.singletonList(new MergingSuggestDataProvider(dataProviders));
		} else {
			return Collections.unmodifiableList(dataProviders);
		}
	}

	/**
	 * internal constructor for testing
	 * 
	 * @param dataProvider
	 */
	QuerySuggestManager(SuggestDataProvider... dataProvider) {
		suggestDataProviders = prepareSuggestDataProviders(Arrays.asList(dataProvider));
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
		return activeQuerySuggesters.computeIfAbsent(indexName, (_tenant) -> initializeQuerySuggesters(_tenant, synchronous));
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

	/**
	 * Initialize query suggester for all suggest data provider that have data
	 * for the given index name.
	 * 
	 * @param indexName
	 * @param synchronous
	 * @return
	 */
	private QuerySuggester initializeQuerySuggesters(String indexName, boolean synchronous) {
		if (suggestDataProviders.size() == 1) {
			return initializeQuerySuggester(suggestDataProviders.get(0), indexName, synchronous);
		}

		List<QuerySuggester> suggesters = new ArrayList<>();
		for (SuggestDataProvider sdp : suggestDataProviders) {
			if (sdp.hasData(indexName)) {
				suggesters.add(initializeQuerySuggester(sdp, indexName, synchronous));
			}
		}

		if (suggesters.isEmpty()) {
			log.warn("No SuggestDataProvider provides data for index {}. Will use NoopQuerySuggester", indexName);
			return new NoopQuerySuggester(true);
		}
		if (suggesters.size() == 1) {
			return suggesters.get(0);
		}
		else {
			return new CompoundQuerySuggester(suggesters);
		}
	}

	private QuerySuggester initializeQuerySuggester(SuggestDataProvider suggestDataProvider, String indexName, boolean synchronous) {
		if ("noop".equals(indexName)) {
			return new NoopQuerySuggester(true);
		}

		QuerySuggesterProxy updateableQuerySuggester = new QuerySuggesterProxy(indexName);

		Path tenantFolder = suggestIndexFolder.resolve(indexName.toString()).resolve(suggestDataProvider.getClass().getSimpleName());
		SuggesterFactory factory = new LuceneSuggesterFactory(tenantFolder);
		factory.setMetricsRegistry(metricsRegistry);

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

		return updateableQuerySuggester;
	}

	@Override
	public void close() {
		scheduledTasks.values().forEach(t -> t.cancel(true));
		executor.shutdown();
	}

}
