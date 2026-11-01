package com.company.leadershipintelligence.api.response;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record RunImbalancesResponse(UUID scoringRunId, JsonNode imbalances) {
}
