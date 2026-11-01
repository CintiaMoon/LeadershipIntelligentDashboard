package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;
import com.company.leadershipintelligence.domain.valueobject.ScoreValue;
import com.company.leadershipintelligence.domain.valueobject.ScoreWeight;

import java.math.BigDecimal;

/**
 * One dimension in the overall calculation (specv3.md Sections 17.1, 19.2): raw, effective dimension penalty,
 * adjusted = max(0, raw - penalty), weight and weighted contribution (adjusted x weight, full precision).
 * An insufficient dimension has no raw, adjusted or contribution value.
 */
public record AdjustedDimension(
        ScoringDimension dimension,
        ScoreValue rawScore,
        PenaltyAmount dimensionPenalty,
        ScoreValue adjustedScore,
        ScoreWeight weight,
        BigDecimal weightedContribution) {

    public AdjustedDimension {
        boolean present = rawScore != null;
        if (present != (adjustedScore != null) || present != (weightedContribution != null)) {
            throw new IllegalArgumentException("adjusted score and contribution exist exactly when the raw score exists");
        }
        if (present && adjustedScore.value().compareTo(rawScore.value()) > 0) {
            throw new IllegalArgumentException("an adjusted score cannot exceed its raw score");
        }
    }
}
