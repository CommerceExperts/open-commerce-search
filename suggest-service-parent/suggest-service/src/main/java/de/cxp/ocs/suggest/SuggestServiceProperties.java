package de.cxp.ocs.suggest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig.SortStrategy;
import de.cxp.ocs.smartsuggest.spi.SuggestConfigProvider;
import lombok.NonNull;

/**
 * <p>
 * A configuration wrapper around all required settings. All settings can be
 * configured as environment variables or system properties. System properties
 * are always preferred over environment variables.
 * </p>
 * <p>
 * Only the environment variable names are documented - the System Properties
 * are the same, just in the lowercase form and with dots instead underscores
 * (e.g. instead 'SUGGEST_SERVER_PORT' it is 'suggest.server.port').
 * </p>
 */
public class SuggestServiceProperties implements SuggestConfigProvider {

	private final Properties				properties;
	private final Function<String, String>	getEnvAccess;

	private static final String SUGGEST_PROPERTY_PREFIX = "suggest.";

	/**
	 * Expects integer value
	 * 
	 * @see {@link SuggestConfig::setMaxSharpenedQueries}
	 */
	private static final String PROPERTY_MAX_SHARPENED_QUERIES = "max-sharpened-queries";

	/**
	 * Expects boolean value
	 * 
	 * @see {@link SuggestConfig::setUseDataSourceMerger}
	 */
	private final static String PROPERTY_DATA_SOURCE_MERGER = "data-source-merger";

	/**
	 * Expects boolean value
	 * 
	 * @see {@link SuggestConfig::setAlwaysDoFuzzy}
	 */
	private static final String PROPERTY_ALWAYS_DO_FUZZY = "always-do-fuzzy";

	/**
	 * Expects string which is literally one of:
	 * <ul>
	 * <li>MatchGroupsSeparated</li>
	 * <li>PrimaryAndSecondaryByWeight</li>
	 * </ul>
	 * 
	 * @see {@link SuggestConfig.SortStrategy}
	 * @see {@link SuggestConfig::setSortStrategy}
	 */
	private static final String PROPERTY_SORT_STRATEGY = "sort-strategy";

	/**
	 * Expects integer value
	 *
	 * @see {@link SuggestConfig::setPrefetchLimitFactor}
	 */
	private final static String PROPERTY_GROUP_PREFETCH_LIMIT_FACTOR = "group.prefetch-limit-factor";

	/**
	 * Expects string value
	 * 
	 * @see {@link SuggestConfig::setGroupKey}
	 */
	private final static String PROPERTY_GROUP_KEY = "group.key";

	/**
	 * <p>
	 * Expects the property in the format 'group1=0.x,group2=0.x' to be used as group-share configuration for the
	 * {@link de.cxp.ocs.smartsuggest.limiter.ConfigurableShareLimiter}.
	 * </p>
	 * <p>
	 * This limiter is only used, if 'suggest.group.key' is defined as well and it is preferred over 'group.cutoff.conf'
	 * in case both configurations exist.
	 * </p>
	 * 
	 * @see {@link SuggestConfig::setUseRelativeShareLimit}
	 * @see {@link SuggestConfig::addGroupConfig}
	 */
	private final static String PROPERTY_GROUP_SHARE_CONF = "group.share-conf";

	/**
	 * <p>
	 * Expects the property in the format 'group1=N,group2=M' where each group should be defined with a value > 0.
	 * It requires 'suggest.group.key' to be set and the names of that key must be the same as specified here.
	 * </p>
	 * <p>
	 * If specified, the GroupedCutOffLimiter will be configured with this cut-off limits unless `group.share.conf` is
	 * configured as well.
	 * </p>
	 * 
	 * @see {@link SuggestConfig::addGroupConfig}
	 */
	private final static String PROPERTY_GROUP_CUTOFF_CONF = "group.cutoff-conf";

	/**
	 * If 'suggest.group.key' is defined, this property expects a comma separated list of groups related to it.
	 * 
	 * @see {@link SuggestConfig::setGroupDeduplicationOrder}
	 */
	private static final String PROPERTY_GROUP_DEDUPLICATION_ORDER = "group.deduplication-order";

	/**
	 * Expects language tag as string
	 * 
	 * @see {@link Locale::forLanguageTag}
	 */
	private static final String PROPERTY_LOCALE = "locale";

	public SuggestServiceProperties() {
		this(System.getProperties());
	}

	public SuggestServiceProperties(Properties properties) {
		this(properties, System::getenv);
	}

	// only for testing
	SuggestServiceProperties(Properties properties, Function<String, String> getEnvAccess) {
		this.properties = properties;
		this.getEnvAccess = getEnvAccess;
	}

	public SuggestServiceProperties(@NonNull InputStream stream) {
		getEnvAccess = System::getenv;
		properties = new Properties(System.getProperties());
		try {
			properties.load(stream);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Failed to load custom suggest properties", e);
		}
	}

	public SuggestConfig getDefaultSuggestConfig() {
		return loadSuggestConfig(new SuggestConfig(), null);
	}

	private SuggestConfig loadSuggestConfig(SuggestConfig baseConfig, String customPropertyInfix) {
		getPropertyValue(PROPERTY_GROUP_PREFETCH_LIMIT_FACTOR)
				.map(Integer::parseInt)
				.ifPresent(baseConfig::setPrefetchLimitFactor);

		getPropertyValue(PROPERTY_DATA_SOURCE_MERGER, customPropertyInfix)
				.map(Boolean::parseBoolean)
				.ifPresent(baseConfig::setUseDataSourceMerger);

		getPropertyValue(PROPERTY_GROUP_KEY, customPropertyInfix)
				.ifPresent(baseConfig::setGroupKey);

		getPropertyValue(PROPERTY_GROUP_SHARE_CONF, customPropertyInfix)
				.map(rawConf -> toLinkedHashMap(rawConf, Double::parseDouble))
				.ifPresent(
						groupConfMap -> {
							baseConfig.setUseRelativeShareLimit(true);
							groupConfMap.entrySet().forEach(e -> baseConfig.addGroupConfig(e.getKey(), (int) (e.getValue() * 100)));
						});

		if (!baseConfig.isUseRelativeShareLimit()) {
			getPropertyValue(PROPERTY_GROUP_CUTOFF_CONF, customPropertyInfix)
					.map(rawConf -> toLinkedHashMap(rawConf, Integer::parseInt))
					.ifPresent(
							groupConfMap -> groupConfMap.entrySet().forEach(
									e -> baseConfig.addGroupConfig(e.getKey(), e.getValue())));
		}

		getPropertyValue(PROPERTY_GROUP_DEDUPLICATION_ORDER, customPropertyInfix)
				.map(val -> StringUtils.split(val, ','))
				.ifPresent(baseConfig::setGroupDeduplicationOrder);

		getPropertyValue(PROPERTY_ALWAYS_DO_FUZZY, customPropertyInfix)
				.map(Boolean::parseBoolean)
				.ifPresent(baseConfig::setAlwaysDoFuzzy);

		getPropertyValue(PROPERTY_LOCALE, customPropertyInfix)
				.map(Locale::forLanguageTag)
				.ifPresent(baseConfig::setLocale);

		getPropertyValue(PROPERTY_MAX_SHARPENED_QUERIES, customPropertyInfix)
				.map(Integer::parseInt)
				.ifPresent(baseConfig::setMaxSharpenedQueries);

		getPropertyValue(PROPERTY_SORT_STRATEGY, customPropertyInfix)
				.map(SortStrategy::valueOf)
				.ifPresent(baseConfig::setSortStrategy);

		return baseConfig;
	}

	@Override
	public SuggestConfig getConfig(String indexName, SuggestConfig modifiableConfig) {
		Optional<String> safeIndexName = validateIndexName(indexName);
		return safeIndexName.map(name -> loadSuggestConfig(modifiableConfig, name)).orElse(modifiableConfig);
	}

	private Optional<String> validateIndexName(String indexName) {
		if (StringUtils.isEmpty(indexName)) return Optional.empty();
		StringBuilder safeIndexName = new StringBuilder();
		boolean lastCharIsDash = false;
		for (char c : indexName.trim().toCharArray()) {
			if (c == '\\' || c == '=' || Character.isWhitespace(c)) {
				c = '-';
			}
			if (c == '-') {
				if (!lastCharIsDash) {
					safeIndexName.append(c);
				}
				lastCharIsDash = true;
			}
			else {
				safeIndexName.append(c);
				lastCharIsDash = false;
			}
		}
		return safeIndexName.length() == 0 ? Optional.empty() : Optional.of(safeIndexName.toString());
	}

	/**
	 * Expects env var 'SUGGEST_SERVER_PORT' set to a valid port number.
	 * Defaults to 8081.
	 * 
	 * @return
	 */
	public int getServerPort() {
		return getPropertyValue("server.port")
				.map(Integer::parseInt)
				.orElse(8081);
	}

	/**
	 * Expects env var 'SUGGEST_SERVER_ADDRESS' set to a valid server address.
	 * Defaults to "0.0.0.0".
	 * 
	 * @return
	 */
	public String getServerAdress() {
		return getPropertyValue("server.address")
				.orElse("0.0.0.0");
	}

	/**
	 * <p>
	 * Expects env var 'SUGGEST_UPDATE_RATE' set to an integer between 5 and
	 * 3600. It's used as the interval on how often the SuggestDataProviders are
	 * asked if they have new data.
	 * </p>
	 * search-test-ocssuggest-6b65dd5598-jz7z5
	 * <p>
	 * Defaults to 60.
	 * </p>
	 * 
	 * @return
	 */
	public int getUpdateRateInSeconds() {
		return getPropertyValue("update-rate")
				.map(Integer::parseInt)
				.orElse(60);
	}

	/**
	 * Expects the env var SUGGEST_PRELOAD_INDEXES as a comma separated list of
	 * all index names that should be initialized and loaded on startup.
	 * 
	 * @return
	 */
	public String[] getPreloadIndexes() {
		return getPropertyValue("preload-indexes")
				.map(s -> s.split(","))
				.orElse(new String[0]);
	}

	/**
	 * <p>
	 * Expects the env var 'SUGGEST_INDEX_FOLDER' to name a index-folder that
	 * should be used for lucene to store its data.
	 * </p>
	 * <p>
	 * Defaults to a temporary directory with the prefix "ocs_suggest".
	 * </p>
	 * 
	 * @return
	 */
	public Path getIndexFolder() {
		Optional<String> indexFolder = getPropertyValue("index-folder");
		if (indexFolder.isPresent()) {
			return Paths.get(indexFolder.get());
		}
		else {
			try {
				return Files.createTempDirectory("ocs_suggest");
			}
			catch (IOException e) {
				throw new UncheckedIOException("Could not create temporary suggest index directory", e);
			}
		}
	}

	/**
	 * <p>
	 * Expects the env var 'SUGGESTER_MAX_IDLE_MINUTES' or the system property
	 * 'suggester.max.idle.minutes' to be set to an integer value.
	 * Defaults to 30.
	 * </p>
	 * <p>
	 * It's used to close suggesters if unused for that specified time. In case
	 * a request comes in for that suggester again, it is initialized
	 * asynchronously. This means it will be available after a few seconds
	 * (depending on the backing suggest-data-providers) after the first
	 * request.
	 * </p>
	 * 
	 * @return
	 */
	public int getSuggesterMaxIdleMinutes() {
		return getPropertyValue("service.max-idle-minutes")
				// legacy property used "suggester" instead "suggest" as prefix
				.or(() -> getFullPropertyValue("suggester.max-idle-minutes"))
				.map(Integer::parseInt).orElse(30);
	}

	/**
	 * Prefix for /health and /metrics path. Should start with a slash and end
	 * without. Defaults to empty string.
	 * 
	 * @return
	 */
	public String getManagementPathPrefix() {
		return getPropertyValue("service.mgmt-path-prefix")
				.or(() -> getPropertyValue("mgmt-path-prefix"))
				.orElse("");
	}

	private <T> LinkedHashMap<String, T> toLinkedHashMap(String rawConf, Function<String, T> valParser) {
		String[] splitConf = StringUtils.split(rawConf, ',');
		LinkedHashMap<String, T> map = new LinkedHashMap<>(splitConf.length);
		for (String split : splitConf) {
			String[] confPair = StringUtils.split(split, "=", 2);
			if (confPair.length == 2) {
				Optional.ofNullable(valParser.apply(confPair[1]))
						.ifPresent(value -> map.put(confPair[0], value));
			}
		}
		return map;
	}

	private Optional<String> getPropertyValue(String propertyName, String customInfix) {
		return StringUtils.isEmpty(customInfix)
				? getFullPropertyValue(SUGGEST_PROPERTY_PREFIX + propertyName)
				: getFullPropertyValue(SUGGEST_PROPERTY_PREFIX + customInfix + "." + propertyName);
	}

	private Optional<String> getPropertyValue(String propName) {
		return getFullPropertyValue(SUGGEST_PROPERTY_PREFIX + propName);
	}

	private Optional<String> getFullPropertyValue(String fullClassifiedPropertyName) {
		String value = properties.getProperty(fullClassifiedPropertyName);
		if (value == null) {
			// legacy support where coherent property names where split by dot
			// but now are separated by dash
			value = properties.getProperty(fullClassifiedPropertyName.replace('-', '.'));
		}
		if (value == null) {
			value = getEnvAccess.apply(StringUtils.replaceChars(fullClassifiedPropertyName, ".-", "__").toUpperCase(Locale.ROOT));
		}
		return Optional.ofNullable(value);
	}

}
