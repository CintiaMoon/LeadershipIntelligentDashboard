package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.OutOfRangePolicy;
import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.enums.ScaleDirection;

import java.math.BigDecimal;

/** SCALE configuration (specv3.md Section 8.5). */
public record ScaleConfig(
        BigDecimal min,
        BigDecimal max,
        ScaleDirection direction,
        OutOfRangePolicy outOfRangePolicy,
        boolean allowDecimals) implements NormalizationConfig {

    public ScaleConfig {
        ConfigChecks.require(min != null && max != null, "SCALE min and max are required");
        ConfigChecks.require(min.compareTo(max) < 0, "SCALE min must be lower than max");
        ConfigChecks.require(direction != null, "SCALE direction is required");
        ConfigChecks.require(outOfRangePolicy != null, "SCALE outOfRangePolicy is required");
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.SCALE;
    }
}
