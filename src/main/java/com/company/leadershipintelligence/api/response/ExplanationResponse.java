package com.company.leadershipintelligence.api.response;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * The persisted explanation trace, never recalculated (specv3.md Section 28.4). When {@code redacted} is true the
 * per-question values were removed for the caller's role (specv3.md Section 19.5).
 */
public record ExplanationResponse(UUID scoringRunId, boolean redacted, JsonNode trace) {
}
