package com.company.leadershipintelligence.repository.persistence.entity;

import com.company.leadershipintelligence.domain.enums.ConfigurationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One immutable-once-active version of a configuration. The payload is the configuration document as JSON. */
@Entity
@Table(name = "scoring_configuration_version")
public class ScoringConfigurationVersionEntity {

    @Id
    private UUID id;

    @Column(name = "configuration_id", nullable = false)
    private UUID configurationId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConfigurationStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "config_hash", nullable = false)
    private String configHash;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    protected ScoringConfigurationVersionEntity() {
    }

    public ScoringConfigurationVersionEntity(UUID id, UUID configurationId, String tenantId, int version,
                                             ConfigurationStatus status, String payload, String configHash,
                                             String createdBy, Instant createdAt) {
        this.id = id;
        this.configurationId = configurationId;
        this.tenantId = tenantId;
        this.version = version;
        this.status = status;
        this.payload = payload;
        this.configHash = configHash;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConfigurationId() {
        return configurationId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public int getVersion() {
        return version;
    }

    public ConfigurationStatus getStatus() {
        return status;
    }

    public String getPayload() {
        return payload;
    }

    public String getConfigHash() {
        return configHash;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    /** Replaces the content; only valid while DRAFT (the database trigger enforces this too). */
    public void replaceContent(String newPayload, String newHash) {
        this.payload = newPayload;
        this.configHash = newHash;
    }

    public void moveTo(ConfigurationStatus next) {
        this.status = next;
    }

    public void approve(String approver, Instant approvedAt, Instant effectiveFrom) {
        this.approvedBy = approver;
        this.approvedAt = approvedAt;
        this.effectiveFrom = effectiveFrom;
    }
}
