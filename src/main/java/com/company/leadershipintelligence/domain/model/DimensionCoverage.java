package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.CoverageStatus;

import java.math.BigDecimal;

/** Coverage record required by specv3.md Section 10.6; coverage = observed weight / expected weight (D18). */
public record DimensionCoverage(
        int applicableQuestionCount,
        int answeredQuestionCount,
        int validContributionCount,
        BigDecimal expectedWeight,
        BigDecimal observedWeight,
        BigDecimal coverage,
        BigDecimal requiredCoverage,
        CoverageStatus status) {
}
