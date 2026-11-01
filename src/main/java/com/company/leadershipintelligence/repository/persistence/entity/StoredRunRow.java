package com.company.leadershipintelligence.repository.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A stored run as read back; {@code traceJson} is returned exactly as stored. */
public record StoredRunRow(
        UUID id,
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
        String roundingPolicyVersion,
        String confidenceAlgorithmVersion,
        String status,
        String scoreStatus,
        BigDecimal finalScore,
        BigDecimal weightedAdjustedScore,
        BigDecimal overallPenalty,
        String interpretation,
        String layersJson,
        String traceJson,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        String scopeType,
        String scopeId,
        UUID parentRunId,
        String responseSummariesJson) {

    /** An organization run without response summaries (the earlier form). */
    public StoredRunRow(UUID id, String tenantId, String organizationId, String assessmentId, String snapshotId, String source,
                        String configurationSelection, String engineVersion, String algorithmVersion, String configurationVersion,
                        String configurationHash, String inputHash, String roundingPolicyVersion, String confidenceAlgorithmVersion,
                        String status, String scoreStatus, BigDecimal finalScore, BigDecimal weightedAdjustedScore,
                        BigDecimal overallPenalty, String interpretation, String layersJson, String traceJson, String errorCode,
                        String errorMessage, Instant startedAt, Instant completedAt) {
        this(id, tenantId, organizationId, assessmentId, snapshotId, source, configurationSelection, engineVersion, algorithmVersion,
                configurationVersion, configurationHash, inputHash, roundingPolicyVersion, confidenceAlgorithmVersion, status,
                scoreStatus, finalScore, weightedAdjustedScore, overallPenalty, interpretation, layersJson, traceJson, errorCode,
                errorMessage, startedAt, completedAt, "ORGANIZATION", null, null, null);
    }
}
