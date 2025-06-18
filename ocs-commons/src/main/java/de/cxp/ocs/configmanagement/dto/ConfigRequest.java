package de.cxp.ocs.configmanagement.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ConfigRequest {
    private Map<String, Object> defaultConfig;
    private Map<String, Object> scopedConfig;
}
