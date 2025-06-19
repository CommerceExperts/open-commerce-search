package de.cxp.ocs.configinitializer;

import de.cxp.ocs.api.ConfigEntity;
import de.cxp.ocs.api.ConfigRepository;
import de.cxp.ocs.util.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigInitializer implements ApplicationRunner {
    private final ConfigInitializerProperties initializerProperties;
    private final ConfigRepository configRepository;
    private final JsonMapper jsonMapper;

    @Override
    public void run(ApplicationArguments args) {
        initializerProperties.getServices().forEach((serviceName, configProps) -> {
            boolean exists = configRepository.existsByService(serviceName);
            if (exists) {
                log.info("Config for service '{}' already exists. Skipping initialization.", serviceName);
                return;
            }

            Map<String, Object> normalizedDefault = jsonMapper.normalizeKeys(configProps.getDefaultConfig());
            Map<String, Object> normalizedScoped = jsonMapper.normalizeKeys(configProps.getScopedConfig());

            ConfigEntity configEntity = new ConfigEntity();
            configEntity.setService(serviceName);
            configEntity.setDefaultConfigJson(jsonMapper.writeJson(normalizedDefault));
            configEntity.setScopedConfigJson(jsonMapper.writeJson(normalizedScoped));
            configEntity.setCreatedAt(LocalDateTime.now());
            configEntity.setActive(true);

            configRepository.save(configEntity);
            log.info("Initialized config for service '{}'.", serviceName);
        });
    }
}
