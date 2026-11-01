package com.company.leadershipintelligence.domain.service.normalization;

import com.company.leadershipintelligence.domain.enums.OutOfRangePolicy;
import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.rule.Breakpoint;
import com.company.leadershipintelligence.domain.rule.NormalizationConfig;
import com.company.leadershipintelligence.domain.rule.NumberConfig;
import com.company.leadershipintelligence.domain.strategy.NormalizationResult;
import com.company.leadershipintelligence.domain.strategy.NormalizationStrategy;
import com.company.leadershipintelligence.domain.valueobject.Decimals;
import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NumberNormalizationStrategy implements NormalizationStrategy {

    @Override
    public QuestionType supportedType() {
        return QuestionType.NUMBER;
    }

    @Override
    public String strategyId() {
        return "number";
    }

    @Override
    public String strategyVersion() {
        return "1";
    }

    @Override
    public NormalizationResult normalize(ScoringInput input, QuestionDefinition question, NormalizationConfig configuration) {
        NumberConfig config = (NumberConfig) configuration;
        BigDecimal x = AnswerNumbers.toDecimal(input.answer(), question.questionId(), ScoringErrorCode.INVALID_NUMBER_VALUE);
        boolean clamped = x.compareTo(config.min()) < 0 || x.compareTo(config.max()) > 0;
        if (clamped) {
            if (config.outOfRangePolicy() == OutOfRangePolicy.REJECT) {
                throw new ScoringException(ScoringErrorCode.INVALID_NUMBER_VALUE,
                        "value is outside the configured range", Map.of("questionId", question.questionId()));
            }
            x = x.compareTo(config.min()) < 0 ? config.min() : config.max();
        }
        BigDecimal result = config.breakpoints().isEmpty()
                ? LinearScaling.apply(x, config.min(), config.max(), config.direction())
                : interpolate(x, config.breakpoints());
        return new NormalizationResult(new NormalizedValue(result), Set.of(), false, clamped);
    }

    private static BigDecimal interpolate(BigDecimal x, List<Breakpoint> points) {
        for (int i = 1; i < points.size(); i++) {
            Breakpoint from = points.get(i - 1);
            Breakpoint to = points.get(i);
            if (x.compareTo(to.input()) <= 0) {
                BigDecimal rise = to.output().value().subtract(from.output().value());
                BigDecimal run = to.input().subtract(from.input());
                BigDecimal offset = Decimals.divide(x.subtract(from.input()).multiply(rise), run);
                return from.output().value().add(offset);
            }
        }
        return points.get(points.size() - 1).output().value();
    }
}
