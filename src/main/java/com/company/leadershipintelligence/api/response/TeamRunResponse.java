package com.company.leadershipintelligence.api.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The result for one team of an assessment (decision D45). A team that could not be scored at all, because a required
 * question had too few answers, has no run and shows INSUFFICIENT_DATA. A score is never shown for a team without
 * enough answers. Teams are never ranked or compared by the engine.
 */
public record TeamRunResponse(String teamId, UUID scoringRunId, String scoreStatus, BigDecimal leadershipIntelligenceScore,
                              String interpretation) {
}
