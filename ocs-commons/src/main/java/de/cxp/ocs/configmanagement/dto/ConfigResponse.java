package de.cxp.ocs.configmanagement.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ConfigResponse {
    private Long id;
    private String service;
    private Map<String, Object> defaultConfig;
    private Map<String, Object> scopedConfig;
    private LocalDateTime createdAt;
    private boolean isActive;
}