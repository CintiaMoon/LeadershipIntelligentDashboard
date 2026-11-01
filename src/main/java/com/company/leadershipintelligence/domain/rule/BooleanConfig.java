package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;

/** BOOLEAN configuration (specv3.md Section 8.7). Values may be reversed for negative questions. */
public record BooleanConfig(NormalizedValue trueValue, NormalizedValue falseValue) implements NormalizationConfig {

    public BooleanConfig {
        ConfigChecks.require(trueValue != null && falseValue != null, "BOOLEAN trueValue and falseValue are required");
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.BOOLEAN;
    }
}
