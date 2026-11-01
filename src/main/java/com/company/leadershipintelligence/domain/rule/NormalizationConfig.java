package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.QuestionType;

/** Normalization configuration, resolved from the question definition, never from a request. */
public sealed interface NormalizationConfig
        permits ScaleConfig, NumberConfig, MultipleChoiceConfig, MultiSelectConfig, BooleanConfig, TextConfig {

    QuestionType questionType();
}
