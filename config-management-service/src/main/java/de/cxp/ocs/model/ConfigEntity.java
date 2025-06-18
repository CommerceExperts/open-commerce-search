package de.cxp.ocs.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class ConfigEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String service;

    @Column(columnDefinition = "jsonb")
    private String defaultConfigJson;

    @Column(columnDefinition = "jsonb")
    private String scopedConfigJson;

    private LocalDateTime createdAt;
    private boolean isActive;
}