package de.cxp.ocs.conf.configmanagement;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ocs.config-management")
@Getter
@Setter
public class ConfigProperties {
    private boolean enabled;
    private String url;
}
