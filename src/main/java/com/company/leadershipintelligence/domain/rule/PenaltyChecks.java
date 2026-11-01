package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;

/** Structural checks for penalty configuration. */
final class PenaltyChecks {

    private PenaltyChecks() {
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new ScoringException(ScoringErrorCode.INVALID_PENALTY_CONFIGURATION, message);
        }
    }
}
