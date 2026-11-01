package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;

import java.math.BigDecimal;

/**
 * One label range over decimal scores (decision D6). Ranges are half-open [min, max); the last range of a
 * configuration is also closed at 100.
 */
public record InterpretationRange(BigDecimal min, BigDecimal max, String label) {

    public InterpretationRange {
        if (min == null || max == null || label == null || label.isBlank() || min.compareTo(max) >= 0) {
            throw new ScoringException(ScoringErrorCode.INVALID_INTERPRETATION_CONFIGURATION,
                    "range needs min below max and a label");
        }
    }
}
