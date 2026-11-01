package com.company.leadershipintelligence.domain.strategy;

import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.rule.NormalizationConfig;

/** Strategy per question type (specv3.md Section 8.4). Strategies only see present answers. */
public interface NormalizationStrategy {

    QuestionType supportedType();

    String strategyId();

    String strategyVersion();

    NormalizationResult normalize(ScoringInput input, QuestionDefinition question, NormalizationConfig configuration);
}
