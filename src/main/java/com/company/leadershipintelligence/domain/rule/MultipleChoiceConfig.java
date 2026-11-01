package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.enums.UnknownOptionPolicy;
import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** MULTIPLE_CHOICE configuration (specv3.md Section 8.6): every option maps to an explicit value. */
public record MultipleChoiceConfig(
        Map<String, NormalizedValue> values,
        UnknownOptionPolicy unknownOptionPolicy) implements NormalizationConfig {

    public MultipleChoiceConfig {
        ConfigChecks.require(values != null && !values.isEmpty(), "MULTIPLE_CHOICE needs at least one option");
        ConfigChecks.require(unknownOptionPolicy != null, "MULTIPLE_CHOICE unknownOptionPolicy is required");
        values = Collections.unmodifiableSortedMap(new TreeMap<>(values));
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.MULTIPLE_CHOICE;
    }
}
