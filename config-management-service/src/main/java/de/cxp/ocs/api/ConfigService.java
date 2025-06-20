package de.cxp.ocs.api;

import de.cxp.ocs.configmanagement.dto.ConfigHistory;
import de.cxp.ocs.configmanagement.dto.ConfigRequest;
import de.cxp.ocs.configmanagement.dto.ConfigResponse;
import de.cxp.ocs.configmanagement.dto.RollbackRequest;
import de.cxp.ocs.configmanagement.util.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ConfigService {
    private final ConfigRepository configRepository;
    private final JsonMapper jsonMapper;

    public ConfigResponse getLatest(String service) {
        ConfigEntity config = configRepository.findFirstByServiceAndIsActiveTrueOrderByCreatedAtDesc(service)
                .orElseThrow(() -> new RuntimeException("No active config found"));
        return toConfigResponse(config);
    }

    public void createNewConfig(String service, ConfigRequest request) {
        configRepository.deactivateAllByService(service);

        ConfigEntity config = new ConfigEntity();
        config.setService(service);
        config.setDefaultConfigJson(jsonMapper.writeJson(request.getDefaultConfig()));
        config.setScopedConfigJson(jsonMapper.writeJson(request.getScopedConfig()));
        config.setCreatedAt(LocalDateTime.now());
        config.setActive(true);
        configRepository.save(config);
    }

    public Page<ConfigHistory> getHistory(String service, Pageable pageable) {
        return configRepository.findByServiceOrderByCreatedAtDesc(service, pageable)
                .map(this::toConfigHistory);
    }

    public void rollback(String service, RollbackRequest request) {
        ConfigEntity config = configRepository.findByIdAndService(request.getConfigId(), service)
                .orElseThrow(() -> new RuntimeException("Config not found"));

        configRepository.deactivateAllByService(service);
        config.setActive(true);
        configRepository.save(config);
    }

    private ConfigHistory toConfigHistory(ConfigEntity config) {
        ConfigHistory configHistory = new ConfigHistory();
        configHistory.setConfigId(config.getId());
        configHistory.setActive(config.isActive());
        configHistory.setCreatedAt(config.getCreatedAt());
        return configHistory;
    }

    private ConfigResponse toConfigResponse(ConfigEntity config) {
        ConfigResponse response = new ConfigResponse();
        response.setId(config.getId());
        response.setService(config.getService());
        response.setDefaultConfig(jsonMapper.readJson(config.getDefaultConfigJson()));
        response.setScopedConfig(jsonMapper.readJson(config.getScopedConfigJson()));
        response.setCreatedAt(config.getCreatedAt());
        response.setActive(config.isActive());
        return response;
    }

}