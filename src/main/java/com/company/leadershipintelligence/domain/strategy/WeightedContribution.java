package com.company.leadershipintelligence.domain.strategy;

import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;
import com.company.leadershipintelligence.domain.valueobject.ScoreWeight;

/** A valid, applicable input with a positive weight. */
public record WeightedContribution(String questionId, NormalizedValue value, ScoreWeight weight) {

    public WeightedContribution {
        if (weight.isZero()) {
            throw new IllegalArgumentException("zero-weight contributions must be excluded before aggregation");
        }
    }
}
