package de.cxp.ocs.smartsuggest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.google.common.collect.Iterators;

import de.cxp.ocs.smartsuggest.limiter.ConfigurableShareLimiter;
import de.cxp.ocs.smartsuggest.limiter.CutOffLimiter;
import de.cxp.ocs.smartsuggest.limiter.GroupedCutOffLimiter;
import de.cxp.ocs.smartsuggest.limiter.Limiter;
import de.cxp.ocs.smartsuggest.monitoring.Instrumentable;
import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import de.cxp.ocs.smartsuggest.querysuggester.*;
import de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneSuggesterFactory;
import de.cxp.ocs.smartsuggest.spi.*;
import de.cxp.ocs.smartsuggest.spi.standard.CompoundSuggestConfigProvider;
import de.cxp.ocs.smartsuggest.spi.standard.DefaultSuggestConfigProvider;
import de.cxp.ocs.smartsuggest.updater.SuggestionsUpdater;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.search.RequiredSearch;
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

	private static final String INDEX_NAME_TAG = "indexName";

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

	private SuggestConfig defaultSuggestConfig;

	/**
	 * This builder should be used to set up the QuerySuggestManager
	 */
	public static class QuerySuggestManagerBuilder {

		private Path suggestIndexFolder;

		private int updateRate = 60;

		private Set<String> preloadIndexes = new HashSet<>();

		private MeterRegistryAdapter metricsRegistry;

		private Limiter defaultLimiter;

		private Map<String, Map<String, Object>> dataProviderConfigs = new HashMap<>(1);

		@NonNull
		private SuggestConfig defaultSuggestConfig = new SuggestConfig();

		private List<SuggestDataProvider> injectedSuggestDataProvider = new ArrayList<>();

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
		 * set update rate without validation.
		 * 
		 * @internal for testing
		 * @param seconds
		 * @return builder
		 */
		QuerySuggestManagerBuilder updateRateUnbound(int seconds) {
			updateRate = seconds;
			return this;
		}

		/**
		 * Deprecated! Only Lucene suggester implemented at the moment!
		 * 
		 * @param engine
		 *        engine to use
		 * @deprecated only Lucene suggester implemented at the moment
		 * @return fluid builder
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
		 *        class name of all data provider instances that should receive this config.
		 * @param config
		 *        the configuration
		 * @return fluid builder
		 */
		public QuerySuggestManagerBuilder addDataProviderConfig(String canonicalClassName, Map<String, Object> config) {
			dataProviderConfigs.put(canonicalClassName, config);
			return this;
		}

		/**
		 * Same as {@code QuerySuggestManagerBuilder.addDataProviderConfig(String, Map)}
		 * 
		 * @param sdpClazz
		 *        class of all data provider instances that should receive this config.
		 * @param config
		 * @return the builder itself
		 */
		public QuerySuggestManagerBuilder addDataProviderConfig(Class<? extends SuggestDataProvider> sdpClazz, Map<String, Object> config) {
			dataProviderConfigs.put(sdpClazz.getCanonicalName(), config);
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
		 *        a limiter implementation
		 * @return fluid builder
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
		 * @return fluid builder
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
		 *         fluid builder
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
		 * @return fluid builder
		 */
		public QuerySuggestManagerBuilder addMetricsRegistryAdapter(MeterRegistryAdapter reg) {
			this.metricsRegistry = reg;
			return this;
		}

		/**
		 * Add default suggest config that should be used in case no
		 * SuggestConfigProvider exists or no provider has a config for a
		 * certain index.
		 * This config is also forwarded to the suggest config providers in
		 * case they only overwrite specific values.
		 * 
		 * @param defaultSuggestConfig
		 *        default suggest config object
		 * @return fluid builder
		 */
		public QuerySuggestManagerBuilder withDefaultSuggestConfig(@NonNull
		SuggestConfig defaultSuggestConfig) {
			this.defaultSuggestConfig = defaultSuggestConfig;
			return this;
		}

		/**
		 * <p>
		 * Add a custom {@link SuggestDataProvider}. Several instances of the same class can be added.
		 * </p>
		 * <p>
		 * If the same class is also available via ServiceLoader it is loaded once more!
		 * </p>
		 * <p>
		 * In case a DataProviderConfig (Map) is set, it will be passed to all instances of the according class.
		 * </p>
		 * 
		 * @param additionalSuggestDataProvider
		 * @return
		 */
		public QuerySuggestManagerBuilder withSuggestDataProvider(SuggestDataProvider additionalSuggestDataProvider) {
			injectedSuggestDataProvider.add(additionalSuggestDataProvider);
			return this;
		}

		/**
		 * Build QuerySuggestManager that can manage multiple query suggesters.
		 * 
		 * @return the manager
		 */
		public QuerySuggestManager build() {
			suggestIndexFolder = ensureSuggestIndexLoaded();

			QuerySuggestManager querySuggestManager = new QuerySuggestManager(Optional.ofNullable(metricsRegistry), injectedSuggestDataProvider, dataProviderConfigs, defaultSuggestConfig);
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

		private Path ensureSuggestIndexLoaded() {
			if (suggestIndexFolder == null) {
				try {
					suggestIndexFolder = Files.createTempDirectory("smartsuggest-");
				}
				catch (IOException e) {
					throw new UncheckedIOException("required suggestIndexFolder not set and cant be created", e);
				}
			}
			return suggestIndexFolder;
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
	private QuerySuggestManager(Optional<MeterRegistryAdapter> meterRegistryAdapter, List<SuggestDataProvider> additionalSuggestDataProviders, Map<String, Map<String, Object>> dataProviderConfig, SuggestConfig defaultSuggestConfig) {
		this.defaultSuggestConfig = defaultSuggestConfig;
		suggestDataProviders = loadDataProviders(meterRegistryAdapter, additionalSuggestDataProviders, dataProviderConfig);
		suggestConfigProvider = loadConfigProviders();
	}

	private List<SuggestDataProvider> loadDataProviders(Optional<MeterRegistryAdapter> meterRegistryAdapter, List<SuggestDataProvider> additionalSuggestDataProviders, Map<String, Map<String, Object>> dataProviderConfig) {
		ServiceLoader<SuggestDataProvider> serviceLoader = ServiceLoader.load(SuggestDataProvider.class);
		Iterator<SuggestDataProvider> loadedSDPs = Iterators.concat(serviceLoader.iterator(), additionalSuggestDataProviders.iterator());
		List<SuggestDataProvider> dataProviders = new ArrayList<>();
		if (!loadedSDPs.hasNext()) {
			throw new IllegalStateException("No SuggestDataProvider found on classpath! Suggest unusable that way."
					+ " Please provide a SuggestDataProvider implementation accessible via ServiceLoader.");
		}
		while (loadedSDPs.hasNext()) {
			try {
				// due to the possibility to inject additionalSuggestDataProviders it is possible
				// that two SuggestDataProvider of the same class are injected. We accept this, as
				// this way it's possible to have a common SuggestDataProvider that can provide
				// different kind of data or data for different indexes. However we can't deduplicate
				// the config here. This has to be done at the instantiation of that according SDP
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
			for (SuggestDataProvider sdp : dataProviders) {
				if (sdp instanceof Instrumentable) {
					Iterable<Tag> tags = Tags.of("dataProvider", sdp.getClass().getCanonicalName());
					((Instrumentable) sdp).instrument(meterRegistryAdapter, tags);
				}
			}
		}
		return dataProviders;
	}

	private SuggestConfigProvider loadConfigProviders() {
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
			return new CompoundSuggestConfigProvider(configProviders);
		}
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
	public QuerySuggester getQuerySuggester(@NonNull
	String indexName) {
		return getQuerySuggester(indexName, false);
	}

	public QuerySuggester getQuerySuggester(@NonNull
	String indexName, boolean synchronous) {
		ScheduledFuture<?> scheduledFuture = scheduledTasks.get(indexName);
		if (scheduledFuture != null && scheduledFuture.isDone()) {
			scheduledTasks.remove(indexName);
			activeQuerySuggesters.remove(indexName);
		}
		return activeQuerySuggesters.computeIfAbsent(indexName, (_tenant) -> initializeQuerySuggesters(_tenant, synchronous));
	}

	public void destroyQuerySuggester(String indexName) throws Exception {
		ScheduledFuture<?> scheduledFuture = scheduledTasks.remove(indexName);
		if (scheduledFuture != null && !scheduledFuture.isDone()) {
			scheduledFuture.cancel(true);
		}
		QuerySuggester removedSuggester = activeQuerySuggesters.remove(indexName);
		if (removedSuggester != null) {
			removedSuggester.destroy();

			this.metricsRegistry.ifPresent(a -> {
				var registry = a.getMetricsRegistry();
				RequiredSearch.in(registry)
						.tag(INDEX_NAME_TAG, indexName)
						.meters()
						.forEach(m -> registry.remove(m).close());
			});
		}
		// remove last (?) reference and suggest garbage collection
		removedSuggester = null;
		System.gc();
	}

	/**
	 * Initialize query suggester for all suggest data provider that have data
	 * for the given index name.
	 * 
	 * @param indexName
	 * @param synchronous
	 * @return
	 */
	private QuerySuggester initializeQuerySuggesters(final String indexName, final boolean synchronous) {
		log.info("Initializing SuggestIndex '{}' {}synchronously", indexName, synchronous ? "" : "a");
		List<SuggestDataProvider> actualSuggestDataProviders = suggestDataProviders.stream()
				.filter(sdp -> {
					try {
						boolean hasData = sdp.hasData(indexName);
						if (!hasData) {
							log.info("SuggestDataProvider of type {} has no data for index {} - skipping.", sdp.getClass().getSimpleName(), indexName);
						}
						return hasData;
					}
					catch (Exception e) {
						// FIXME: a single case of a temporary problem (e.g. connection failed) will skip that SDP for the runtime
						// catch potential Runtime Exceptions
						log.warn("SuggestDataProvider of type {} caused unexpected Exception", sdp.getClass().getCanonicalName(), e);
						return false;
					}
				})
				.collect(Collectors.toList());

		if (actualSuggestDataProviders.isEmpty()) {
			log.warn("No SuggestDataProvider provides data for index {}. Will use NoopQuerySuggester", indexName);

			// schedule a task that will cause a invalidation as soon as "done".
			ScheduledFuture<?> scheduledTask = executor.schedule(() -> log.info("Invalidating NoopQuerySuggester for index '{}'", indexName), 10, TimeUnit.MINUTES);
			scheduledTasks.put(indexName, scheduledTask);

			return new NoopQuerySuggester(true);
		}

		SuggestConfig suggestConfig = enforceSuggestConfig(indexName);
		Optional<Limiter> limiter = createLimiter(suggestConfig);

		final QuerySuggester actualQuerySuggester;
		if (actualSuggestDataProviders.size() == 1) {
			actualQuerySuggester = initializeQuerySuggester(actualSuggestDataProviders.get(0), indexName, suggestConfig, synchronous);
		}
		else if (suggestConfig.useDataSourceMerger) {
			actualQuerySuggester = initializeQuerySuggester(new MergingSuggestDataProvider(actualSuggestDataProviders), indexName, suggestConfig, synchronous);
		}
		else {
			List<QuerySuggester> suggesters = new ArrayList<>();
			for (SuggestDataProvider sdp : actualSuggestDataProviders) {
				suggesters.add(initializeQuerySuggester(sdp, indexName, suggestConfig, synchronous));
			}
			actualQuerySuggester = new CompoundQuerySuggester(suggesters, suggestConfig);
			if (limiter.isPresent()) {
				((CompoundQuerySuggester) actualQuerySuggester).setDoLimitFinalResult(false);
			}
		}

		return limiter
				.map(_limiter -> (QuerySuggester) new GroupingSuggester(actualQuerySuggester, _limiter).setPrefetchLimitFactor(suggestConfig.getPrefetchLimitFactor()))
				.orElse(actualQuerySuggester);
	}

	private SuggestConfig enforceSuggestConfig(String indexName) {
		// use clone here, because suggest config providers are able to modify the default suggest config
		SuggestConfig clonedConfig = defaultSuggestConfig.clone();
		SuggestConfig suggestConfig = suggestConfigProvider.getConfig(indexName, clonedConfig);
		// in case the suggest config provider returned null, we fall back to the cloned config
		if (suggestConfig == null) suggestConfig = clonedConfig;
		return suggestConfig;
	}

	private Optional<Limiter> createLimiter(SuggestConfig suggestConfig) {
		if (suggestConfig.getGroupKey() != null) {
			if (suggestConfig.useRelativeShareLimit) {
				LinkedHashMap<String, Double> internalGroupConfig = new LinkedHashMap<>();
				suggestConfig.getGroupConfig().forEach(groupConfig -> internalGroupConfig.put(groupConfig.groupName, (double) groupConfig.limit));
				return Optional.of(new ConfigurableShareLimiter(suggestConfig.getGroupKey(), internalGroupConfig, suggestConfig.groupDeduplicationOrder));
			}
			else {
				LinkedHashMap<String, Integer> internalGroupConfig = new LinkedHashMap<>();
				suggestConfig.getGroupConfig().forEach(groupConfig -> internalGroupConfig.put(groupConfig.groupName, groupConfig.limit));
				Integer cutoffDefault = internalGroupConfig.getOrDefault(CommonPayloadFields.PAYLOAD_TYPE_OTHER, 5);
				return Optional.of(new GroupedCutOffLimiter(suggestConfig.getGroupKey(), cutoffDefault, internalGroupConfig, suggestConfig.groupDeduplicationOrder));
			}
		}
		else {
			return Optional.empty();
		}
	}

	private QuerySuggester initializeQuerySuggester(SuggestDataProvider suggestDataProvider, String indexName, SuggestConfig suggestConfig, boolean synchronous) {
		if ("noop".equals(indexName)) {
			return new NoopQuerySuggester(true);
		}

		Iterable<Tag> tags = Tags
				.of(INDEX_NAME_TAG, indexName)
				.and("dataProvider", suggestDataProvider.getClass().getCanonicalName());

		QuerySuggesterProxy updateableQuerySuggester = new QuerySuggesterProxy(indexName, suggestDataProvider.getClass().getCanonicalName());
		updateableQuerySuggester.instrument(metricsRegistry, tags);

		Path tenantFolder = suggestIndexFolder.resolve(indexName.toString()).resolve(suggestDataProvider.getClass().getCanonicalName());
		SuggesterFactory<?> factory = new LuceneSuggesterFactory(tenantFolder);
		factory.instrument(metricsRegistry, tags);

		SuggestionsUpdater updateTask = new SuggestionsUpdater(suggestDataProvider, suggestConfigProvider, suggestConfig, indexName, updateableQuerySuggester, factory);
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
