package de.cxp.ocs.elasticsearch;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.elasticsearch.client.RestHighLevelClient;

import de.cxp.ocs.config.ConnectionConfiguration;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SettingsProxy {


	private final Properties properties;

	private ConnectionConfiguration connectionConf;

	public SettingsProxy() {
		properties = loadPropertiesResource("ocs-suggest.default.properties")
				.map(defaultProps -> new Properties(defaultProps))
				.orElseGet(() -> new Properties());

		loadPropertiesResource("ocs-suggest.properties").ifPresent(p -> properties.putAll(p));
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

	public String get(String propertyName) {
		String property = System.getProperty(propertyName);
		if (property == null) {
			property = properties.getProperty(propertyName);
		}
		return property;
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
		}
		return connectionConf;
	}
	public Optional<Boolean> isIndexEnabled(String indexName) {
		return Optional.ofNullable(get("suggest.index." + indexName)).map(Boolean::parseBoolean);
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
				log.warn("source field {} was configured but does not exist at index {}", sourceField, indexName);
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
