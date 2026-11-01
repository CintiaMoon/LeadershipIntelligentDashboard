package com.company.leadershipintelligence.api.response;

import java.math.BigDecimal;

/** One dimension in a scoring result. An insufficient dimension has no numeric score fields (specv3.md Section 20.3). */
public record DimensionResponse(
        String status,
        BigDecimal rawScore,
        BigDecimal effectivePenalty,
        BigDecimal adjustedScore,
        BigDecimal coverage,
        BigDecimal weight,
        BigDecimal weightedContribution,
        String label) {
}
