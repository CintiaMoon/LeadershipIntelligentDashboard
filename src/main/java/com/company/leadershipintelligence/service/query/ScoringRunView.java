package com.company.leadershipintelligence.service.query;

import com.company.leadershipintelligence.domain.enums.RunStatus;
import com.company.leadershipintelligence.domain.enums.ScoreStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A stored run as the application exposes it. {@code traceJson} is the explanation exactly as stored; a failed run
 * has an error code and no scores or trace and is never a valid result. A TEAM run has the team id as
 * {@code scopeId} and the organization run it was produced with as {@code parentRunId} (decision D45);
 * {@code responseSummariesJson} says how the answers of each question were combined, and is null for a run scored
 * from one answer per question.
 */
public record ScoringRunView(
        UUID id,
        String assessmentId,
        String snapshotId,
        String organizationId,
        String source,
        String configurationSelection,
        String engineVersion,
        String algorithmVersion,
        String configurationVersion,
        String configurationHash,
        String inputHash,
        String roundingPolicyVersion,
        RunStatus status,
        ScoreStatus scoreStatus,
        BigDecimal finalScore,
        BigDecimal weightedAdjustedScore,
        BigDecimal overallPenalty,
        String interpretation,
        String layersJson,
        String traceJson,
        String errorCode,
        Instant startedAt,
        Instant completedAt,
        String scopeType,
        String scopeId,
        UUID parentRunId,
        String responseSummariesJson) {

    /** An organization run without response summaries (the earlier form). */
    public ScoringRunView(UUID id, String assessmentId, String snapshotId, String organizationId, String source,
                          String configurationSelection, String engineVersion, String algorithmVersion, String configurationVersion,
                          String configurationHash, String inputHash, String roundingPolicyVersion, RunStatus status,
                          ScoreStatus scoreStatus, BigDecimal finalScore, BigDecimal weightedAdjustedScore, BigDecimal overallPenalty,
                          String interpretation, String layersJson, String traceJson, String errorCode, Instant startedAt,
                          Instant completedAt) {
        this(id, assessmentId, snapshotId, organizationId, source, configurationSelection, engineVersion, algorithmVersion,
                configurationVersion, configurationHash, inputHash, roundingPolicyVersion, status, scoreStatus, finalScore,
                weightedAdjustedScore, overallPenalty, interpretation, layersJson, traceJson, errorCode, startedAt, completedAt,
                "ORGANIZATION", null, null, null);
    }

    public boolean isTeamRun() {
        return "TEAM".equals(scopeType);
    }
}
