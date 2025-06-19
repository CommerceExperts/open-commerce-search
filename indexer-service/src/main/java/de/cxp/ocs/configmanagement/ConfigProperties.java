package de.cxp.ocs.configmanagement;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "config-management")
@Data
public class ConfigProperties {
    private boolean enabled;
    private String url;
}