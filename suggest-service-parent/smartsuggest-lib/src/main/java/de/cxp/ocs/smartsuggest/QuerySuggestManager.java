package de.cxp.ocs.smartsuggest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
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

import de.cxp.ocs.smartsuggest.limiter.ConfigurableShareLimiter;
import de.cxp.ocs.smartsuggest.limiter.CutOffLimiter;
import de.cxp.ocs.smartsuggest.limiter.GroupedCutOffLimiter;
import de.cxp.ocs.smartsuggest.limiter.Limiter;
import de.cxp.ocs.smartsuggest.monitoring.Instrumentable;
import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import de.cxp.ocs.smartsuggest.querysuggester.CompoundQuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.NoopQuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggesterProxy;
import de.cxp.ocs.smartsuggest.querysuggester.SuggesterEngine;
import de.cxp.ocs.smartsuggest.querysuggester.SuggesterFactory;
import de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneSuggesterFactory;
import de.cxp.ocs.smartsuggest.spi.CommonPayloadFields;
import de.cxp.ocs.smartsuggest.spi.MergingSuggestDataProvider;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestConfigProvider;
import de.cxp.ocs.smartsuggest.spi.SuggestDataProvider;
import de.cxp.ocs.smartsuggest.spi.standard.CompoundSuggestConfigProvider;
import de.cxp.ocs.smartsuggest.spi.standard.DefaultSuggestConfigProvider;
import de.cxp.ocs.smartsuggest.updater.SuggestionsUpdater;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
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

	private final SuggestConfigProvider suggestConfigProvider;

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

	private Optional<MeterRegistryAdapter> metricsRegistry = Optional.empty();

	public Limiter defaultLimiter;

	public static class QuerySuggestManagerBuilder {

		private Path suggestIndexFolder;

		private int updateRate = 60;

		private Set<String> preloadIndexes = new HashSet<>();

		private MeterRegistryAdapter metricsRegistry;

		private Limiter defaultLimiter;

		private Map<String, Map<String, Object>> dataProviderConfigs = new HashMap<>(1);

		private SuggestConfig defaultSuggestConfig = new SuggestConfig();

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
		 * Deprecated! Only Lucene suggester implemented at the moment!
		 * 
		 * @param engine
		 *        engine to use
		 * @deprecated only Lucene suggester implemented at the moment
		 * @return the changed builder
		 */
		@Deprecated
		public QuerySuggestManagerBuilder engine(SuggesterEngine engine) {
			if (SuggesterEngine.DHIMAN.equals(engine)) {
				throw new UnsupportedOperationException("DHIMAN suggester is not implemented anymore");
			}
			return this;
		}

		/**
		 * Add configuration for a specific data provider that will be loaded by
		 * your environment. It is only applied, if the according data provider
		 * is loaded.
		 * 
		 * @param canonicalClassName
		 * @param config
		 * @return
		 */
		public QuerySuggestManagerBuilder addDataProviderConfig(String canonicalClassName, Map<String, Object> config) {
			dataProviderConfigs.put(canonicalClassName, config);
			return this;
		}

		/**
		 * <p>
		 * With this method you can specify a default limiter for
		 * suggestions from different sources.
		 * </p>
		 * <p>
		 * This limiter is only used, if an index uses several data sources but
		 * no grouping-key is defined.
		 * </p>
		 * 
		 * @see de.cxp.ocs.smartsuggest.limiter.Limiter
		 * @see de.cxp.ocs.smartsuggest.limiter.GroupedCutOffLimiter
		 * @see de.cxp.ocs.smartsuggest.limiter.ConfigurableShareLimiter
		 * @see de.cxp.ocs.smartsuggest.limiter.CutOffLimiter
		 * @param customLimiter
		 * @return
		 */
		public QuerySuggestManagerBuilder withLimiter(Limiter customLimiter) {
			defaultLimiter = customLimiter;
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
		 * @deprecated use SuggestConfigProvider to change this value per index
		 *             or set defaultSuggestConfig instead.
		 * @return builder
		 */
		@Deprecated
		public QuerySuggestManagerBuilder useDataMerger() {
			this.defaultSuggestConfig.setUseDataSourceMerger(true);
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

		public QuerySuggestManagerBuilder withDefaultSuggestConfig(SuggestConfig defaultSuggestConfig) {
			this.defaultSuggestConfig = defaultSuggestConfig;
			return this;
		}

		public QuerySuggestManager build() {
			if (suggestIndexFolder == null) {
				throw new IllegalArgumentException("required 'indexFolder' not specified");
			}
			QuerySuggestManager querySuggestManager = new QuerySuggestManager(Optional.ofNullable(metricsRegistry), dataProviderConfigs, defaultSuggestConfig);
			querySuggestManager.suggestIndexFolder = suggestIndexFolder;
			querySuggestManager.updateRate = updateRate;
			querySuggestManager.defaultLimiter = this.defaultLimiter != null ? this.defaultLimiter : new CutOffLimiter();
			querySuggestManager.metricsRegistry = Optional.ofNullable(metricsRegistry);
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
	 * 
	 * @param meterRegistryAdapter
	 * @param defaultSuggestConfig
	 */
	private QuerySuggestManager(Optional<MeterRegistryAdapter> meterRegistryAdapter, Map<String, Map<String, Object>> dataProviderConfig, SuggestConfig defaultSuggestConfig) {
		suggestDataProviders = loadDataProviders(meterRegistryAdapter, dataProviderConfig);
		suggestConfigProvider = loadConfigProviders(defaultSuggestConfig);
	}

	private List<SuggestDataProvider> loadDataProviders(Optional<MeterRegistryAdapter> meterRegistryAdapter, Map<String, Map<String, Object>> dataProviderConfig) {
		ServiceLoader<SuggestDataProvider> serviceLoader = ServiceLoader.load(SuggestDataProvider.class);
		Iterator<SuggestDataProvider> loadedSDPs = serviceLoader.iterator();
		List<SuggestDataProvider> dataProviders = new ArrayList<>();
		if (!loadedSDPs.hasNext()) {
			throw new IllegalStateException("No SuggestDataProvider found on classpath! Suggest unusable that way."
					+ " Please provide a SuggestDataProvider implementation accessible via ServiceLoader.");
		}
		while (loadedSDPs.hasNext()) {
			try {
				SuggestDataProvider sdp = loadedSDPs.next();
				Map<String, Object> sdpConfig = dataProviderConfig.get(sdp.getClass().getCanonicalName());
				if (sdpConfig != null) {
					sdp.configure(sdpConfig);
				}
				dataProviders.add(sdp);
				log.info("initialized SmartSuggest with {}", sdp.getClass().getCanonicalName());
			}
			catch (Exception e) {
				log.info("failed to load a SuggestDataProvider", e);
			}
		}
		
		if (meterRegistryAdapter.isPresent()) {
			for(SuggestDataProvider sdp : dataProviders) {
				if (sdp instanceof Instrumentable) {
					Iterable<Tag> tags = Tags.of("dataProvider", sdp.getClass().getCanonicalName());
					((Instrumentable) sdp).instrument(meterRegistryAdapter, tags);
				}
			}
		}
		return dataProviders;
	}

	private SuggestConfigProvider loadConfigProviders(SuggestConfig defaultSuggestConfig) {
		ServiceLoader<SuggestConfigProvider> serviceLoader = ServiceLoader.load(SuggestConfigProvider.class);
		Iterator<SuggestConfigProvider> loadedConfigProviders = serviceLoader.iterator();
		if (!loadedConfigProviders.hasNext()) {
			log.info("No SuggestConfigProvider found. Using default.");
			return new DefaultSuggestConfigProvider(defaultSuggestConfig);
		}
		else {
			List<SuggestConfigProvider> configProviders = new ArrayList<>();
			loadedConfigProviders.forEachRemaining(configProviders::add);
			Collections.sort(configProviders, Comparator.comparingInt(SuggestConfigProvider::getPriority));
			// add default config provider to make sure the config is never null
			configProviders.add(new DefaultSuggestConfigProvider(defaultSuggestConfig));
			return new CompoundSuggestConfigProvider(configProviders);
		}
	}

	/**
	 * internal constructor for testing
	 * 
	 * @param dataProvider
	 */
	QuerySuggestManager(SuggestConfigProvider configProvider, SuggestDataProvider... dataProvider) {
		suggestDataProviders = Arrays.asList(dataProvider);
		suggestConfigProvider = configProvider;
		defaultLimiter = new CutOffLimiter();
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
		List<SuggestDataProvider> actualSuggestDataProviders = suggestDataProviders.stream()
				.filter(sdp -> sdp.hasData(indexName))
				.collect(Collectors.toList());

		if (actualSuggestDataProviders.isEmpty()) {
			log.warn("No SuggestDataProvider provides data for index {}. Will use NoopQuerySuggester", indexName);
			return new NoopQuerySuggester(true);
		}
		if (actualSuggestDataProviders.size() == 1) {
			return initializeQuerySuggester(actualSuggestDataProviders.get(0), indexName, synchronous);
		}

		SuggestConfig suggestConfig = suggestConfigProvider.getConfig(indexName);
		if (suggestConfig.useDataSourceMerger) {
			return initializeQuerySuggester(new MergingSuggestDataProvider(actualSuggestDataProviders), indexName, synchronous);
		}
		else {
			List<QuerySuggester> suggesters = new ArrayList<>();
			for (SuggestDataProvider sdp : actualSuggestDataProviders) {
				suggesters.add(initializeQuerySuggester(sdp, indexName, synchronous));
			}
			Limiter limiter = createLimiter(suggestConfig);
			return new CompoundQuerySuggester(suggesters, limiter);
		}
	}

	private Limiter createLimiter(SuggestConfig suggestConfig) {
		if (suggestConfig.getGroupKey() != null) {
			if (suggestConfig.useRelativeShareLimit) {
				LinkedHashMap<String, Double> internalGroupConfig = new LinkedHashMap<>();
				suggestConfig.getGroupConfig().forEach(groupConfig -> internalGroupConfig.put(groupConfig.groupName, (double) groupConfig.limit));
				return new ConfigurableShareLimiter(suggestConfig.getGroupKey(), internalGroupConfig, suggestConfig.groupDeduplicationOrder);
			}
			else {
				LinkedHashMap<String, Integer> internalGroupConfig = new LinkedHashMap<>();
				suggestConfig.getGroupConfig().forEach(groupConfig -> internalGroupConfig.put(groupConfig.groupName, groupConfig.limit));
				Integer cutoffDefault = internalGroupConfig.getOrDefault(CommonPayloadFields.PAYLOAD_TYPE_OTHER, 5);
				return new GroupedCutOffLimiter(suggestConfig.getGroupKey(), cutoffDefault, internalGroupConfig, suggestConfig.groupDeduplicationOrder);
			}
		}
		else {
			return defaultLimiter;
		}
	}

	private QuerySuggester initializeQuerySuggester(SuggestDataProvider suggestDataProvider, String indexName, boolean synchronous) {
		if ("noop".equals(indexName)) {
			return new NoopQuerySuggester(true);
		}

		Iterable<Tag> tags = Tags
				.of("indexName", indexName)
				.and("dataProvider", suggestDataProvider.getClass().getCanonicalName());

		QuerySuggesterProxy updateableQuerySuggester = new QuerySuggesterProxy(indexName, suggestDataProvider.getClass().getCanonicalName());
		updateableQuerySuggester.instrument(metricsRegistry, tags);

		Path tenantFolder = suggestIndexFolder.resolve(indexName.toString()).resolve(suggestDataProvider.getClass().getCanonicalName());
		SuggesterFactory factory = new LuceneSuggesterFactory(tenantFolder);
		factory.instrument(metricsRegistry, tags);

		SuggestionsUpdater updateTask = new SuggestionsUpdater(suggestDataProvider, suggestConfigProvider, indexName, updateableQuerySuggester, factory);
		updateTask.instrument(metricsRegistry, tags);

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
