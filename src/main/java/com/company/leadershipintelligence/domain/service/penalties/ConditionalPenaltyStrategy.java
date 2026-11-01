package com.company.leadershipintelligence.domain.service.penalties;

import com.company.leadershipintelligence.domain.enums.PenaltyReason;
import com.company.leadershipintelligence.domain.enums.PenaltyStrategyType;
import com.company.leadershipintelligence.domain.model.ConditionResult;
import com.company.leadershipintelligence.domain.rule.StrategyConfig;
import com.company.leadershipintelligence.domain.service.patterns.ConditionEvaluator;
import com.company.leadershipintelligence.domain.strategy.PenaltyCalculation;
import com.company.leadershipintelligence.domain.strategy.PenaltyContext;
import com.company.leadershipintelligence.domain.strategy.PenaltyStrategy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Evaluates an explicit condition (three-valued) and delegates to the configured strategy when it holds. */
public class ConditionalPenaltyStrategy implements PenaltyStrategy {

    private final ConditionEvaluator evaluator;
    private final Map<PenaltyStrategyType, PenaltyStrategy> delegates = new EnumMap<>(PenaltyStrategyType.class);

    public ConditionalPenaltyStrategy(ConditionEvaluator evaluator, List<PenaltyStrategy> delegateStrategies) {
        this.evaluator = evaluator;
        delegateStrategies.forEach(s -> delegates.put(s.supportedType(), s));
    }

    @Override
    public PenaltyStrategyType supportedType() {
        return PenaltyStrategyType.CONDITIONAL;
    }

    @Override
    public PenaltyCalculation calculate(PenaltyContext context, StrategyConfig configuration) {
        StrategyConfig.Conditional config = (StrategyConfig.Conditional) configuration;
        ConditionResult result = evaluator.evaluate(config.condition(), context.evaluation());
        return switch (result.outcome()) {
            case FALSE -> PenaltyCalculation.noDeduction(PenaltyReason.CONDITION_NOT_SATISFIED, null, null);
            case UNKNOWN -> PenaltyCalculation.missing(PenaltyReason.MISSING_SIGNALS, null);
            case TRUE -> delegates.get(config.delegate().type()).calculate(context, config.delegate());
        };
    }
}
