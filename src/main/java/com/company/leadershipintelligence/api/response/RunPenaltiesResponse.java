package com.company.leadershipintelligence.api.response;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/** Candidates, applied and suppressed deductions with reasons (specv3.md Section 28.8). */
public record RunPenaltiesResponse(UUID scoringRunId, JsonNode penalties, JsonNode effectiveByTarget, String tieBreakVersion,
                                   boolean redacted) {
}
