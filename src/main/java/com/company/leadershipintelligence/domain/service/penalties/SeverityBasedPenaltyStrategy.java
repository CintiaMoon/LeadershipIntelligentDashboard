package com.company.leadershipintelligence.domain.service.penalties;

import com.company.leadershipintelligence.domain.enums.PenaltyStrategyType;
import com.company.leadershipintelligence.domain.rule.StrategyConfig;
import com.company.leadershipintelligence.domain.strategy.PenaltyCalculation;
import com.company.leadershipintelligence.domain.strategy.PenaltyContext;
import com.company.leadershipintelligence.domain.strategy.PenaltyStrategy;

import java.math.BigDecimal;

public class SeverityBasedPenaltyStrategy implements PenaltyStrategy {

    @Override
    public PenaltyStrategyType supportedType() {
        return PenaltyStrategyType.SEVERITY_BASED;
    }

    @Override
    public PenaltyCalculation calculate(PenaltyContext context, StrategyConfig configuration) {
        StrategyConfig.SeverityBased config = (StrategyConfig.SeverityBased) configuration;
        BigDecimal multiplier = config.multipliers().get(context.trigger().severity());
        return PenaltyCalculation.calculated(config.baseDeduction().value().multiply(multiplier),
                "severity " + context.trigger().severity(), multiplier);
    }
}
