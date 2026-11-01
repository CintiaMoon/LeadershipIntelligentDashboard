package com.company.leadershipintelligence.domain.model;

import java.math.BigDecimal;

/**
 * Factors of the confidence calculation (decision D17, algorithm version 1; version 2 adds participation,
 * decision D45). Freshness is not assessed yet because observations carry no timestamps; it is 1 and
 * {@code freshnessAssessed} is false. {@code participation} and {@code respondents} are null in version 1; in
 * version 2 {@code respondents} is null when none of the supporting questions has a known count (factor 1).
 */
public record ConfidenceBreakdown(
        BigDecimal coverage,
        BigDecimal independence,
        BigDecimal reliability,
        BigDecimal freshness,
        boolean freshnessAssessed,
        int supportingSignals,
        int independentGroups,
        String algorithmVersion,
        BigDecimal participation,
        Integer respondents) {

    public ConfidenceBreakdown(BigDecimal coverage, BigDecimal independence, BigDecimal reliability, BigDecimal freshness,
                               boolean freshnessAssessed, int supportingSignals, int independentGroups, String algorithmVersion) {
        this(coverage, independence, reliability, freshness, freshnessAssessed, supportingSignals, independentGroups,
                algorithmVersion, null, null);
    }
}
