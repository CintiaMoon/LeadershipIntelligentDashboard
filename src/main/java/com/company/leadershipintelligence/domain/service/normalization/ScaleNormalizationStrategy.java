package com.company.leadershipintelligence.domain.service.normalization;

import com.company.leadershipintelligence.domain.enums.OutOfRangePolicy;
import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.rule.NormalizationConfig;
import com.company.leadershipintelligence.domain.rule.ScaleConfig;
import com.company.leadershipintelligence.domain.strategy.NormalizationResult;
import com.company.leadershipintelligence.domain.strategy.NormalizationStrategy;
import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public class ScaleNormalizationStrategy implements NormalizationStrategy {

    @Override
    public QuestionType supportedType() {
        return QuestionType.SCALE;
    }

    @Override
    public String strategyId() {
        return "scale";
    }

    @Override
    public String strategyVersion() {
        return "1";
    }

    @Override
    public NormalizationResult normalize(ScoringInput input, QuestionDefinition question, NormalizationConfig configuration) {
        ScaleConfig config = (ScaleConfig) configuration;
        BigDecimal x = AnswerNumbers.toDecimal(input.answer(), question.questionId(), ScoringErrorCode.INVALID_SCALE_VALUE);
        if (!config.allowDecimals() && x.stripTrailingZeros().scale() > 0) {
            throw invalid(question, "decimal values are not allowed for this question");
        }
        boolean clamped = x.compareTo(config.min()) < 0 || x.compareTo(config.max()) > 0;
        if (clamped) {
            if (config.outOfRangePolicy() == OutOfRangePolicy.REJECT) {
                throw invalid(question, "value is outside the configured range");
            }
            x = x.compareTo(config.min()) < 0 ? config.min() : config.max();
        }
        return new NormalizationResult(new NormalizedValue(
                LinearScaling.apply(x, config.min(), config.max(), config.direction())), Set.of(), false, clamped);
    }

    private static ScoringException invalid(QuestionDefinition question, String message) {
        return new ScoringException(ScoringErrorCode.INVALID_SCALE_VALUE, message,
                Map.of("questionId", question.questionId()));
    }
}
