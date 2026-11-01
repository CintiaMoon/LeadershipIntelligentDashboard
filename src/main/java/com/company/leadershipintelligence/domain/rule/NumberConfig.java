package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.OutOfRangePolicy;
import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.enums.ScaleDirection;

import java.math.BigDecimal;
import java.util.List;

/**
 * NUMBER configuration (specv3.md Section 8.8). With no breakpoints the mapping is linear over
 * [min, max]. With breakpoints the mapping is piecewise linear and direction is carried by the
 * breakpoint outputs.
 */
public record NumberConfig(
        BigDecimal min,
        BigDecimal max,
        ScaleDirection direction,
        OutOfRangePolicy outOfRangePolicy,
        List<Breakpoint> breakpoints) implements NormalizationConfig {

    public NumberConfig {
        ConfigChecks.require(min != null && max != null, "NUMBER min and max are required");
        ConfigChecks.require(min.compareTo(max) < 0, "NUMBER min must be lower than max");
        ConfigChecks.require(direction != null, "NUMBER direction is required");
        ConfigChecks.require(outOfRangePolicy != null, "NUMBER outOfRangePolicy is required");
        breakpoints = breakpoints == null ? List.of() : List.copyOf(breakpoints);
        if (!breakpoints.isEmpty()) {
            ConfigChecks.require(breakpoints.size() >= 2, "NUMBER needs at least two breakpoints");
            ConfigChecks.require(breakpoints.get(0).input().compareTo(min) == 0
                    && breakpoints.get(breakpoints.size() - 1).input().compareTo(max) == 0,
                    "NUMBER breakpoints must start at min and end at max");
            for (int i = 1; i < breakpoints.size(); i++) {
                ConfigChecks.require(breakpoints.get(i).input().compareTo(breakpoints.get(i - 1).input()) > 0,
                        "NUMBER breakpoint inputs must be strictly increasing");
            }
        }
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.NUMBER;
    }
}
