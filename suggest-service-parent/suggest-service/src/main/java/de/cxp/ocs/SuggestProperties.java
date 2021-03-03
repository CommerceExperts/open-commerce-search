package de.cxp.ocs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

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
public class SuggestProperties {

	/**
	 * Expects env var 'SUGGEST_SERVER_PORT' set to a valid port number.
	 * Defaults to 8081.
	 * 
	 * @return
	 */
	public int getServerPort() {
		return getVarValue("SUGGEST_SERVER_PORT")
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
		return getVarValue("SUGGEST_SERVER_ADDRESS")
				.orElse("0.0.0.0");
	}

	/**
	 * <p>
	 * Expects env var 'SUGGEST_UPDATE_RATE' set to an integer between 5 and
	 * 3600. It's used as the interval on how often the SuggestDataProviders are
	 * asked if they have new data.
	 * </p>
	 * <p>
	 * Defaults to 60.
	 * </p>
	 * 
	 * @return
	 */
	public int getUpdateRateInSeconds() {
		return getVarValue("SUGGEST_UPDATE_RATE")
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
		return getVarValue("SUGGEST_PRELOAD_INDEXES")
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
		Optional<String> indexFolder = getVarValue("SUGGEST_INDEX_FOLDER");
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
	 * 'suggester_max_idle_minutes' to be set to an integer value.
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
		return getVarValue("SUGGESTER_MAX_IDLE_MINUTES")
				.map(Integer::parseInt)
				.orElse(30);
	}

	/**
	 * <p>
	 * Expects the env var 'SUGGEST_GROUP_KEY' to be set to a string value. If
	 * set, it will
	 * be used to extract that particular payload value and group the
	 * suggestions accordingly.
	 * </p>
	 * It's recommended to specify 'SUGGEST_GROUP_SHARE_CONF' or
	 * 'SUGGEST_GROUP_CUTOFF_CONF' as well, otherwise the default limiter will
	 * be used after grouping.
	 * </p>
	 * 
	 * @return grouping key
	 */
	public Optional<String> getGroupKey() {
		return getVarValue("SUGGEST_GROUP_KEY");
	}

	/**
	 * <p>
	 * Expects the env var 'SUGGEST_GROUP_SHARE_CONF' in the format
	 * 'group1=0.x,group2=0.x' to be used as group-share configuration for the
	 * {@link de.cxp.ocs.smartsuggest.limiter.ConfigurableShareLimiter}.
	 * </p>
	 * <p>
	 * This limiter is only used, if 'SUGGEST_GROUP_KEY' is defined as well. But
	 * this limiter is prefered over 'GroupedCutOffLimiter' (in case both
	 * configurations exist).
	 * </p>
	 * 
	 * @return
	 */
	public Optional<LinkedHashMap<String, Double>> getGroupedShareConf() {
		return getVarValue("SUGGEST_GROUP_SHARE_CONF")
				.map(rawConf -> toLinkedHashMap(rawConf, Double::parseDouble));
	}

	/**
	 * <p>
	 * Expects the env var 'SUGGEST_GROUP_CUTOFF_CONF' to be specified in the
	 * format 'group1=N,group2=M'. Also requires 'SUGGEST_GROUP_KEY' to be set.
	 * </p>
	 * <p>
	 * If specified, the GroupedCutOffLimiter will be configured with this
	 * cut-off limits (but not if 'SUGGEST_GROUP_SHARE_CONF' is defined as
	 * well).
	 * </p>
	 * 
	 * @return cut-off limits configuration for GroupedCutOffLimiter
	 */
	public Optional<LinkedHashMap<String, Integer>> getGroupedCutoffConf() {
		return getVarValue("SUGGEST_GROUP_CUTOFF_CONF")
				.map(rawConf -> toLinkedHashMap(rawConf, Integer::parseInt));
	}

	/**
	 * <p>
	 * Expects the env var 'SUGGEST_GROUP_CUTOFF_DEFAULT' to be set to an
	 * integer value.
	 * </p>
	 * <p>
	 * Only retrieved, if 'SUGGEST_GROUP_KEY' is set and
	 * 'SUGGEST_GROUP_SHARE_CONF' does not exist.
	 * It's used as the default limit for the GroupedCutOffLimiter.
	 * </p>
	 * <p>
	 * Defaults to 5.
	 * </p>
	 * 
	 * @return default limit for GroupedCutOffLimiter
	 */
	public Integer getGroupedCutoffDefaultSize() {
		return getVarValue("SUGGEST_GROUP_CUTOFF_DEFAULT")
				.map(Integer::parseInt)
				.orElse(5);
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

	private Optional<String> getVarValue(String envVarName) {
		String propName = envVarName.toLowerCase(Locale.ROOT).replaceAll("_", ".");
		String value = System.getProperty(propName);
		if (value == null) {
			value = System.getenv(envVarName);
		}
		return Optional.ofNullable(value);
	}
}
