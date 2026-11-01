package com.company.leadershipintelligence.domain.service.normalization;

import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.rule.BooleanConfig;
import com.company.leadershipintelligence.domain.rule.NormalizationConfig;
import com.company.leadershipintelligence.domain.strategy.NormalizationResult;
import com.company.leadershipintelligence.domain.strategy.NormalizationStrategy;

import java.util.Map;

public class BooleanNormalizationStrategy implements NormalizationStrategy {

    @Override
    public QuestionType supportedType() {
        return QuestionType.BOOLEAN;
    }

    @Override
    public String strategyId() {
        return "boolean";
    }

    @Override
    public String strategyVersion() {
        return "1";
    }

    @Override
    public NormalizationResult normalize(ScoringInput input, QuestionDefinition question, NormalizationConfig configuration) {
        BooleanConfig config = (BooleanConfig) configuration;
        if (!(input.answer() instanceof Boolean answer)) {
            throw new ScoringException(ScoringErrorCode.INVALID_ANSWER_TYPE,
                    "answer for question " + question.questionId() + " must be a boolean",
                    Map.of("questionId", question.questionId()));
        }
        return NormalizationResult.ofValue(answer ? config.trueValue() : config.falseValue());
    }
}
