package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.valueobject.ScoreWeight;

/** One question-to-dimension mapping entry (specv3.md Section 10.2). A zero weight is allowed but excluded. */
public record ContributionRule(String questionId, ScoringDimension dimension, ScoreWeight weight) {

    public ContributionRule {
        if (questionId == null || questionId.isBlank() || dimension == null || weight == null) {
            throw new com.company.leadershipintelligence.domain.exception.ScoringException(
                    com.company.leadershipintelligence.domain.exception.ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION,
                    "questionId, dimension and weight are required");
        }
    }
}
