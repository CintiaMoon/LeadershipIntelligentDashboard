package com.company.leadershipintelligence.api.response;

import java.math.BigDecimal;

public record OverallResponse(BigDecimal weightedAdjustedScore, BigDecimal effectiveOverallPenalty, BigDecimal finalScore) {
}
