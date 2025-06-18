package de.cxp.ocs.config.configmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.cxp.ocs.config.ApplicationProperties;
import de.cxp.ocs.config.ApplicationSearchProperties;
import de.cxp.ocs.configmanagement.dto.ConfigResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigLoader {
    private final ApplicationProperties appProps;
    private final ConfigProperties configProps;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void fetchAndApplyExternalConfig() {
        if (!configProps.isEnabled()) {
            log.info("External config management disabled. Using local application properties.");
            return;
        }

        try {
            ConfigResponse response = fetchConfigFromService();

            if (response == null) {
                log.warn("Received null response from config management service.");
                return;
            }

            applyDefaultTenantConfig(response);
            applyScopedTenantConfigs(response);

            log.info("Successfully loaded and applied external config.");

        } catch (Exception ex) {
            log.error("Failed to fetch external config", ex);
        }
    }

    private ConfigResponse fetchConfigFromService() {
        log.info("Fetching external config from URL: {}", configProps.getUrl());
        return restTemplate.getForObject(configProps.getUrl(), ConfigResponse.class);
    }

    private void applyDefaultTenantConfig(ConfigResponse response) {
        ApplicationSearchProperties defaultConfig = convertToSearchProperties(response.getDefaultConfig());
        appProps.setDefaultTenantConfig(defaultConfig);
    }

    private void applyScopedTenantConfigs(ConfigResponse response) {
        Map<String, ApplicationSearchProperties> scopedConfigs = response.getScopedConfig()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> convertToSearchProperties(entry.getValue())
                ));

        appProps.setTenantConfig(scopedConfigs);
    }

    private ApplicationSearchProperties convertToSearchProperties(Object rawConfig) {
        return objectMapper.convertValue(rawConfig, ApplicationSearchProperties.class);
    }
}
