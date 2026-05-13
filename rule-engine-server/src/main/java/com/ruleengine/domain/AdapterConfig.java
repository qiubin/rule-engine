package com.ruleengine.domain;

import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "adapter_config")
@Data
public class AdapterConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @Column(name = "base_url", length = 256)
    private String baseUrl;

    @Column(name = "adapter_path", length = 128)
    private String adapterPath = "/api/v1/adapter/emr";

    @Column(name = "auth_type", length = 16)
    private String authType = "none";

    @Column(name = "auth_token", length = 512)
    private String authToken;

    @Column(name = "api_key", length = 256)
    private String apiKey;

    @Column(name = "connect_timeout_ms")
    private Integer connectTimeoutMs = 5000;

    @Column(name = "read_timeout_ms")
    private Integer readTimeoutMs = 10000;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
