package de.cxp.ocs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.cxp.ocs.configmanagement.dto.ConfigRequest;
import de.cxp.ocs.configmanagement.dto.ConfigResponse;
import de.cxp.ocs.configmanagement.dto.RollbackRequest;
import de.cxp.ocs.model.ConfigEntity;
import de.cxp.ocs.repository.ConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConfigService {

    private final ConfigRepository configRepository;
    private final ObjectMapper objectMapper;

    public ConfigResponse getLatest(String service) {
        ConfigEntity config = configRepository.findFirstByServiceAndIsActiveTrueOrderByCreatedAtDesc(service)
                .orElseThrow(() -> new RuntimeException("No active config found"));
        return toResponse(config);
    }

    public void createNewConfig(String service, ConfigRequest request) {
        configRepository.deactivateAllByService(service);

        ConfigEntity config = new ConfigEntity();
        config.setService(service);
        config.setDefaultConfigJson(writeJson(request.getDefaultConfig()));
        config.setScopedConfigJson(writeJson(request.getScopedConfig()));
        config.setCreatedAt(LocalDateTime.now());
        config.setActive(true);
        configRepository.save(config);
    }

    public Page<ConfigResponse> getHistory(String service, Pageable pageable) {
        return configRepository.findByServiceOrderByCreatedAtDesc(service, pageable)
                .map(this::toResponse);
    }

    public void rollback(String service, RollbackRequest request) {
        ConfigEntity config = configRepository.findByIdAndService(request.getConfigId(), service)
                .orElseThrow(() -> new RuntimeException("Config not found"));

        configRepository.deactivateAllByService(service);
        config.setActive(true);
        configRepository.save(config);
    }

    private ConfigResponse toResponse(ConfigEntity config) {
        ConfigResponse response = new ConfigResponse();
        response.setId(config.getId());
        response.setService(config.getService());
        response.setDefaultConfig(readJson(config.getDefaultConfigJson()));
        response.setScopedConfig(readJson(config.getScopedConfigJson()));
        response.setCreatedAt(config.getCreatedAt());
        response.setActive(config.isActive());
        return response;
    }

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse config", e);
        }
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to write config", e);
        }
    }
}