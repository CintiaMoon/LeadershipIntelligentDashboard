package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.valueobject.AssessmentId;
import com.company.leadershipintelligence.domain.valueobject.AssessmentSnapshotId;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;

import java.util.Objects;

/**
 * Trusted, server-built context for one scoring operation (specv3.md Sections 7.1, 22.1). It is
 * never populated from a client-supplied tenant or organization.
 */
public record ScoringContext(
        TenantId tenantId,
        OrganizationId organizationId,
        AssessmentId assessmentId,
        AssessmentSnapshotId assessmentSnapshotId,
        ConfigurationVersion configurationVersion,
        String engineVersion,
        String algorithmVersion) {

    public ScoringContext {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(assessmentId, "assessmentId");
        Objects.requireNonNull(assessmentSnapshotId, "assessmentSnapshotId");
        Objects.requireNonNull(configurationVersion, "configurationVersion");
        if (engineVersion == null || engineVersion.isBlank() || algorithmVersion == null || algorithmVersion.isBlank()) {
            throw new IllegalArgumentException("engineVersion and algorithmVersion are required");
        }
    }
}
