package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;

/** Structural checks for condition expressions. */
final class ConditionChecks {

    private ConditionChecks() {
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new ScoringException(ScoringErrorCode.INVALID_CONDITION_EXPRESSION, message);
        }
    }
}
