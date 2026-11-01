package com.company.leadershipintelligence.repository.persistence.entity;

import com.company.leadershipintelligence.domain.model.ScoringResult;

import java.time.Instant;
import java.util.UUID;

/**
 * A completed run ready to be stored together with its identity and provenance. A team run has scope TEAM, the
 * team id and the organization run it was produced with (decision D45).
 */
public record NewRun(
        UUID runId,
        ScoringResult result,
        String tenantId,
        String organizationId,
        String source,
        String configurationSelection,
        String inputHash,
        String configurationHash,
        String layersJson,
        String traceJson,
        String idempotencyKey,
        String requestHash,
        Instant startedAt,
        Instant completedAt,
        String scopeType,
        String scopeId,
        UUID parentRunId,
        String responseSummariesJson) {

    /** An organization run with no recorded response summaries (the earlier form). */
    public NewRun(UUID runId, ScoringResult result, String tenantId, String organizationId, String source,
                  String configurationSelection, String inputHash, String configurationHash, String layersJson, String traceJson,
                  String idempotencyKey, String requestHash, Instant startedAt, Instant completedAt) {
        this(runId, result, tenantId, organizationId, source, configurationSelection, inputHash, configurationHash, layersJson,
                traceJson, idempotencyKey, requestHash, startedAt, completedAt, "ORGANIZATION", null, null, null);
    }
}
