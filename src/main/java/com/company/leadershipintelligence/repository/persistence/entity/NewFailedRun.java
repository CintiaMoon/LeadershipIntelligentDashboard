package com.company.leadershipintelligence.repository.persistence.entity;

import java.time.Instant;
import java.util.UUID;

/** A failed run: kept for audit with its error, never exposed as a valid result (specv3.md Section 26.9). */
public record NewFailedRun(
        UUID runId,
        String tenantId,
        String organizationId,
        String assessmentId,
        String snapshotId,
        String source,
        String configurationSelection,
        String engineVersion,
        String algorithmVersion,
        String configurationVersion,
        String configurationHash,
        String inputHash,
        String layersJson,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant completedAt) {
}
