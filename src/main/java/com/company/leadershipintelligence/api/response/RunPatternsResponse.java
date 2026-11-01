package com.company.leadershipintelligence.api.response;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/** Pattern evaluations with the evidence the caller may see (specv3.md Section 28.7). */
public record RunPatternsResponse(UUID scoringRunId, JsonNode patterns, boolean redacted) {
}
