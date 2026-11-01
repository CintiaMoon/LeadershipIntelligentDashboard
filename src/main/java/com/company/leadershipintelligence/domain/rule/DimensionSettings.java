package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;

import java.math.BigDecimal;

/** Per-dimension coverage requirement (decision D18). Sample values are illustrative and unvalidated. */
public record DimensionSettings(BigDecimal requiredCoverage) {

    public DimensionSettings {
        if (requiredCoverage == null || requiredCoverage.signum() < 0 || requiredCoverage.compareTo(BigDecimal.ONE) > 0) {
            throw new ScoringException(ScoringErrorCode.INVALID_COVERAGE_CONFIGURATION,
                    "requiredCoverage must be between 0 and 1");
        }
    }
}
