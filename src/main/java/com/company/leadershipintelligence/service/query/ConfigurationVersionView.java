package com.company.leadershipintelligence.service.query;

import com.company.leadershipintelligence.domain.enums.ConfigurationScope;
import com.company.leadershipintelligence.domain.enums.ConfigurationStatus;

import java.time.Instant;
import java.util.UUID;

/** A configuration version as the application exposes it. Persistence entities never leave the repository layer. */
public record ConfigurationVersionView(
        UUID id,
        UUID configurationId,
        ConfigurationScope scope,
        String scopeId,
        int version,
        ConfigurationStatus status,
        String configHash,
        String createdBy,
        Instant createdAt,
        String approvedBy,
        Instant approvedAt,
        Instant effectiveFrom,
        String payload) {
}
