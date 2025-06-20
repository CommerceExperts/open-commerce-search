package de.cxp.ocs.configmanagement.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConfigHistory {
    private Long configId;
    private boolean isActive;
    private LocalDateTime createdAt;
}
