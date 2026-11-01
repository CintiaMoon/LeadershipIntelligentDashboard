package com.company.leadershipintelligence.repository.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Audit event for a configuration action (specv3.md Section 33.4). The detail never contains assessment content. */
@Entity
@Table(name = "scoring_configuration_audit")
public class ScoringConfigurationAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "configuration_id", nullable = false)
    private UUID configurationId;

    @Column(name = "version_id")
    private UUID versionId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String detail;

    protected ScoringConfigurationAuditEntity() {
    }

    public ScoringConfigurationAuditEntity(String tenantId, UUID configurationId, UUID versionId, String action,
                                           String actor, Instant occurredAt, String detail) {
        this.tenantId = tenantId;
        this.configurationId = configurationId;
        this.versionId = versionId;
        this.action = action;
        this.actor = actor;
        this.occurredAt = occurredAt;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public UUID getConfigurationId() {
        return configurationId;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public String getAction() {
        return action;
    }

    public String getActor() {
        return actor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getDetail() {
        return detail;
    }
}
