package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.enums.UnknownOptionPolicy;
import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * MULTI_SELECT configuration (specv3.md Section 4.2.1, decision D49): every option has an explicit value and the
 * normalized value of an answer is the mean of the values of the options chosen.
 */
public record MultiSelectConfig(
        Map<String, NormalizedValue> values,
        UnknownOptionPolicy unknownOptionPolicy) implements NormalizationConfig {

    public MultiSelectConfig {
        ConfigChecks.require(values != null && !values.isEmpty(), "MULTI_SELECT needs at least one option");
        ConfigChecks.require(unknownOptionPolicy != null, "MULTI_SELECT unknownOptionPolicy is required");
        values = Collections.unmodifiableSortedMap(new TreeMap<>(values));
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.MULTI_SELECT;
    }
}
