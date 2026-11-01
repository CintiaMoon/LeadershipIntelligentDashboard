package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.ContributionStatus;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;
import com.company.leadershipintelligence.domain.valueobject.ScoreWeight;

import java.math.BigDecimal;

/**
 * The effect of one normalized input on one dimension (specv3.md Section 4.5, decision D15). For
 * APPLIED contributions, {@code normalizedWeight} is weight / sum of valid weights and
 * {@code contribution} is value x normalizedWeight, both at full precision. Other statuses carry no
 * normalized weight or contribution, and an excluded input is never counted as zero.
 */
public record DimensionContribution(
        String questionId,
        ScoringDimension dimension,
        NormalizedValue value,
        ScoreWeight weight,
        BigDecimal normalizedWeight,
        BigDecimal contribution,
        ContributionStatus status) {

    public DimensionContribution {
        boolean scored = status == ContributionStatus.APPLIED;
        if (scored != (normalizedWeight != null && contribution != null)) {
            throw new IllegalArgumentException("only APPLIED contributions carry normalizedWeight and contribution");
        }
        if ((status == ContributionStatus.EXCLUDED_NO_VALUE) != (value == null)) {
            throw new IllegalArgumentException("value is absent exactly when the input has no value");
        }
    }
}
