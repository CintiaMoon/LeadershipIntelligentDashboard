package com.company.leadershipintelligence.api.response;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/** Raw and adjusted scores, coverage, contributions and statuses of every dimension of a run (specv3.md Section 28.6). */
public record RunDimensionsResponse(UUID scoringRunId, JsonNode dimensions, JsonNode adjusted, boolean redacted) {
}
