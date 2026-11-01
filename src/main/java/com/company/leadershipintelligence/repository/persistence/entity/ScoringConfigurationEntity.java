package com.company.leadershipintelligence.repository.persistence.entity;

import com.company.leadershipintelligence.domain.enums.ConfigurationScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A configuration slot: one scope (platform, template, organization or tenant) whose versions evolve over time. */
@Entity
@Table(name = "scoring_configuration")
public class ScoringConfigurationEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    private ConfigurationScope scopeType;

    @Column(name = "scope_id")
    private String scopeId;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ScoringConfigurationEntity() {
    }

    public ScoringConfigurationEntity(UUID id, String tenantId, ConfigurationScope scopeType, String scopeId, String name,
                                      Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public ConfigurationScope getScopeType() {
        return scopeType;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
