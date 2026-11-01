package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;

/** Shared configuration validation helpers. */
final class ConfigChecks {

    private ConfigChecks() {
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new ScoringException(ScoringErrorCode.INVALID_NORMALIZATION_CONFIGURATION, message);
        }
    }
}
