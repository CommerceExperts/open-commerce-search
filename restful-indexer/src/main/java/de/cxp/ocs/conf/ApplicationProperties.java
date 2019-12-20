package de.cxp.ocs.conf;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import de.cxp.ocs.config.ConnectionConfiguration;
import lombok.Getter;

/**
 * Properties specific to ocs.
 *
 * <p>
 * Properties are configured in the application.yml file.
 * </p>
 */
@ConfigurationProperties(prefix = "ocs", ignoreUnknownFields = false)
@Component
@Getter
public class ApplicationProperties {

	@NestedConfigurationProperty
	private final ConnectionConfiguration connectionConfiguration = new ConnectionConfiguration();

	@NestedConfigurationProperty
	IndexConfiguration defaultIndexConfig = new IndexConfiguration();

	/**
	 * key = index name
	 */
	@NestedConfigurationProperty
	private final Map<String, IndexConfiguration> indexConfig = new HashMap<>();

}
