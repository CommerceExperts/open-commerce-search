package de.cxp.ocs.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Properties are configured in the application.yml file.
 */
@ConfigurationProperties(prefix = "ocs", ignoreUnknownFields = true)
@Component
@Getter
public class ApplicationProperties {

    private final Set<String> disabledPlugins = new HashSet<>();

    private final Map<String, String> preferedPlugins = new HashMap<>();

    @NestedConfigurationProperty
    private final ConnectionConfiguration connectionConfiguration = new ConnectionConfiguration();

    @NestedConfigurationProperty
    @Setter
    ApplicationSearchProperties defaultTenantConfig = new ApplicationSearchProperties();

    @NestedConfigurationProperty
    @Setter
    private Map<String, ApplicationSearchProperties> tenantConfig = new HashMap<>();

}
