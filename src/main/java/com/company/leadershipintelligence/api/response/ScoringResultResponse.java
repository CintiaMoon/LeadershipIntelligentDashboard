package com.company.leadershipintelligence.api.response;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The scoring result (specv3.md Section 20.1, decision D11). Execution status and score validity are separate;
 * numbers are plain decimals; absent fields are omitted, so a failed or insufficient run shows no invented score.
 * Findings are the stored records, shaped as in the explanation trace.
 *
 * <p>{@code scope} is ORGANIZATION or TEAM (decision D45). A TEAM result names its {@code teamId} and the organization
 * run it belongs to. {@code responses} says how the answers of each question were combined; it is absent for a run scored
 * from one answer per question. {@code teamRuns} lists the team results of an organization run.
 */
public record ScoringResultResponse(
        UUID scoringRunId,
        String assessmentId,
        String assessmentSnapshotId,
        String tenantId,
        String organizationId,
        String source,
        String configurationSelection,
        String engineVersion,
        String algorithmVersion,
        String configurationVersion,
        String configurationHash,
        String inputHash,
        String status,
        String scoreStatus,
        BigDecimal leadershipIntelligenceScore,
        String interpretation,
        Map<String, DimensionResponse> dimensions,
        OverallResponse overall,
        JsonNode imbalances,
        JsonNode patterns,
        JsonNode penalties,
        CountsResponse counts,
        String errorCode,
        Instant calculatedAt,
        UUID explanationReference,
        String notice,
        String scope,
        String teamId,
        UUID organizationScoringRunId,
        JsonNode responses,
        List<TeamRunResponse> teamRuns) {
}
