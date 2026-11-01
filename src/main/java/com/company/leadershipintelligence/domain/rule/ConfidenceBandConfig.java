package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.ConfidenceBand;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;

import java.math.BigDecimal;

/** Lower bounds of the confidence bands (specv3.md Section 14.3). Provisional and configurable. */
public record ConfidenceBandConfig(BigDecimal weakFrom, BigDecimal moderateFrom, BigDecimal strongFrom) {

    public ConfidenceBandConfig {
        if (weakFrom == null || moderateFrom == null || strongFrom == null
                || weakFrom.signum() <= 0 || weakFrom.compareTo(moderateFrom) >= 0
                || moderateFrom.compareTo(strongFrom) >= 0 || strongFrom.compareTo(BigDecimal.ONE) > 0) {
            throw new ScoringException(ScoringErrorCode.INVALID_PATTERN_CONFIGURATION,
                    "confidence band bounds must be ascending within (0, 1]");
        }
    }

    /** V3 illustrative bands: INSUFFICIENT below 0.40, WEAK 0.40, MODERATE 0.60, STRONG 0.80. */
    public static ConfidenceBandConfig illustrative() {
        return new ConfidenceBandConfig(new BigDecimal("0.40"), new BigDecimal("0.60"), new BigDecimal("0.80"));
    }

    public ConfidenceBand bandFor(BigDecimal confidence) {
        if (confidence.compareTo(strongFrom) >= 0) {
            return ConfidenceBand.STRONG;
        }
        if (confidence.compareTo(moderateFrom) >= 0) {
            return ConfidenceBand.MODERATE;
        }
        if (confidence.compareTo(weakFrom) >= 0) {
            return ConfidenceBand.WEAK;
        }
        return ConfidenceBand.INSUFFICIENT;
    }
}
