package com.company.leadershipintelligence.domain.service.coverage;

import com.company.leadershipintelligence.domain.enums.CoverageStatus;
import com.company.leadershipintelligence.domain.model.DimensionCoverage;
import com.company.leadershipintelligence.domain.rule.DimensionSettings;
import com.company.leadershipintelligence.domain.valueobject.Decimals;

import java.math.BigDecimal;

/** Coverage = observed contribution weight / expected contribution weight (decision D18, specv3.md Section 10.6). */
public class CoverageValidationService {

    public DimensionCoverage evaluate(DimensionSettings settings, int applicableQuestions, int answeredQuestions,
                                      int validContributions, BigDecimal expectedWeight, BigDecimal observedWeight) {
        BigDecimal coverage = expectedWeight.signum() == 0
                ? BigDecimal.ZERO
                : Decimals.divide(observedWeight, expectedWeight);
        CoverageStatus status = validContributions > 0 && coverage.compareTo(settings.requiredCoverage()) >= 0
                ? CoverageStatus.SUFFICIENT
                : CoverageStatus.INSUFFICIENT;
        return new DimensionCoverage(applicableQuestions, answeredQuestions, validContributions,
                expectedWeight, observedWeight, coverage, settings.requiredCoverage(), status);
    }
}
