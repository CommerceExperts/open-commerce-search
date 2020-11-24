package de.cxp.ocs.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * Properties are configured in the application.yml file.
 */
@ConfigurationProperties(prefix = "ocs", ignoreUnknownFields = false)
@Component
@Getter
public class ApplicationProperties {

	@NestedConfigurationProperty
	private final ConnectionConfiguration connectionConfiguration = new ConnectionConfiguration();

	@NestedConfigurationProperty
	TenantSearchConfiguration defaultTenantConfig = new TenantSearchConfiguration();

	@NestedConfigurationProperty
	private final Map<String, TenantSearchConfiguration> tenantConfig = new HashMap<>();

}
