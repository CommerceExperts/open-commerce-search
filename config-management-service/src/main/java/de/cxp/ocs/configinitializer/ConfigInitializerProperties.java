package de.cxp.ocs.configinitializer;

import de.cxp.ocs.configmanagement.dto.ConfigRequest;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "config-initializer")
@Data
public class ConfigInitializerProperties {
    private Map<String, ConfigRequest> services;
}
