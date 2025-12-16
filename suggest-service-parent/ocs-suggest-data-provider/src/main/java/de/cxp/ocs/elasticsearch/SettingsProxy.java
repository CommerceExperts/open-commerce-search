package de.cxp.ocs.elasticsearch;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

import org.elasticsearch.client.RestHighLevelClient;

import de.cxp.ocs.config.ConnectionConfiguration;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SettingsProxy {


	private final Properties defaultProperties;

	private ConnectionConfiguration connectionConf;

	public SettingsProxy(Map<String, Object> config) {
		defaultProperties = loadPropertiesResource("ocs-suggest.default.properties")
				.map(Properties::new)
				.orElseGet(Properties::new);

		loadPropertiesResource("ocs-suggest.properties").ifPresent(defaultProperties::putAll);

		defaultProperties.putAll(config);
	}

	private Optional<Properties> loadPropertiesResource(String resourceName) {
		try {
			InputStream propertiesStream = this.getClass().getClassLoader().getResourceAsStream(resourceName);
			if (propertiesStream != null) {
				Properties p = new Properties();
				p.load(propertiesStream);
				return Optional.of(p);
			}
			return Optional.empty();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Get property from the different "layers" in the following order:
	 * - system-properties
	 * - environment variable (using property-name in uppercase and dots replaced by _)
	 * - properties file
	 * - default properties
	 * 
	 * @param propertyName
	 * @return
	 */
	public String get(String propertyName) {
		String value = System.getProperty(propertyName);
		if (value == null) {
			String envVarName = propertyName.toUpperCase().replace('.', '_');
			value = System.getenv(envVarName);
		}
		if (value == null) {
			value = defaultProperties.getProperty(propertyName);
		}
		return value;
	}

	public String get(String propertyName, String defaultValue) {
		String property = get(propertyName);
		return property == null ? defaultValue : property;
	}

	public ConnectionConfiguration getConnectionConfig() {
		if (connectionConf == null) {
			connectionConf = new ConnectionConfiguration();
			connectionConf.setHosts(get("elasticsearch.hosts"));
			connectionConf.setAuth(get("elasticsearch.auth"));
			connectionConf.setUseCompatibilityMode(Boolean.parseBoolean(get("elasticsearch.useCompatibilityMode")));
		}
		return connectionConf;
	}
	public Optional<Boolean> isIndexEnabled(String indexName) {
		return Optional.ofNullable(get("suggest.index." + indexName + ".enable")).map(Boolean::parseBoolean);
	}

	RestHighLevelClient restHighLevelClient;

	public List<Field> getSourceFields(String indexName) throws IOException {
		String sourceFieldProperty = get("suggest.index." + indexName + ".sourceFields");
		if (sourceFieldProperty == null) {
			sourceFieldProperty = get("suggest.index.default.sourceFields");
		}
		if (sourceFieldProperty == null || sourceFieldProperty.isEmpty()) {
			return Collections.emptyList();
		}
		String[] fieldNames = sourceFieldProperty.split(",");

		if (restHighLevelClient == null) {
			restHighLevelClient = new RestHighLevelClient(RestClientBuilderFactory.createRestClientBuilder(getConnectionConfig()));
		}

		FieldConfiguration fieldConfig = new FieldConfigFetcher(restHighLevelClient).fetchConfig(indexName);
		List<Field> sourceFields = new ArrayList<>(fieldNames.length);
		for (String fieldName : fieldNames) {
			Field sourceField = fieldConfig.getField(fieldName);
			if (sourceField != null) {
				sourceFields.add(sourceField);
			}
			else {
				log.warn("source field {} was configured but does not exist at index {}", null, indexName);
			}
		}
		
		return sourceFields;
	}

	public Boolean getIsDeduplicationEnabled(String indexName) {
		String deduplicateProperty = get("suggest.index." + indexName + ".deduplicate");
		if (deduplicateProperty == null) {
			deduplicateProperty = get("suggest.index.default.deduplicate");
		}
		return Boolean.parseBoolean(deduplicateProperty);
	}

	public int getMaxFetchSize(String indexName) {
		String maxFetchSizeProperty = get("suggest.index." + indexName + ".maxFetchSize");
		if (maxFetchSizeProperty == null) {
			maxFetchSizeProperty = get("suggest.index.default.maxFetchSize");
		}
		return Integer.parseInt(maxFetchSizeProperty);
	}
}
