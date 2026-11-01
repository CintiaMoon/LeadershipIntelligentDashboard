package com.company.leadershipintelligence.domain.strategy;

import java.math.BigDecimal;
import java.util.List;

/** Unrounded aggregate plus the per-input shares that sum to it (decision D15). */
public record AggregationResult(BigDecimal score, List<Share> shares) {

    public AggregationResult {
        shares = List.copyOf(shares);
    }

    /** One input's share of the aggregate. */
    public record Share(String questionId, BigDecimal normalizedWeight, BigDecimal contribution) {
    }
}
