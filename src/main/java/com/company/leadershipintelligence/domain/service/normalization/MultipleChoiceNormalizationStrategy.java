package com.company.leadershipintelligence.domain.service.normalization;

import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.enums.UnknownOptionPolicy;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.rule.MultipleChoiceConfig;
import com.company.leadershipintelligence.domain.rule.NormalizationConfig;
import com.company.leadershipintelligence.domain.strategy.NormalizationResult;
import com.company.leadershipintelligence.domain.strategy.NormalizationStrategy;
import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;

import java.util.Map;

public class MultipleChoiceNormalizationStrategy implements NormalizationStrategy {

    @Override
    public QuestionType supportedType() {
        return QuestionType.MULTIPLE_CHOICE;
    }

    @Override
    public String strategyId() {
        return "multiple-choice";
    }

    @Override
    public String strategyVersion() {
        return "1";
    }

    @Override
    public NormalizationResult normalize(ScoringInput input, QuestionDefinition question, NormalizationConfig configuration) {
        MultipleChoiceConfig config = (MultipleChoiceConfig) configuration;
        if (!(input.answer() instanceof String option)) {
            throw new ScoringException(ScoringErrorCode.INVALID_ANSWER_TYPE,
                    "answer for question " + question.questionId() + " must be a string option",
                    Map.of("questionId", question.questionId()));
        }
        NormalizedValue value = config.values().get(option);
        if (value != null) {
            return NormalizationResult.ofValue(value);
        }
        if (config.unknownOptionPolicy() == UnknownOptionPolicy.TREAT_AS_MISSING) {
            return NormalizationResult.missing();
        }
        throw new ScoringException(ScoringErrorCode.INVALID_MULTIPLE_CHOICE_VALUE,
                "answer is not a configured option", Map.of("questionId", question.questionId()));
    }
}
