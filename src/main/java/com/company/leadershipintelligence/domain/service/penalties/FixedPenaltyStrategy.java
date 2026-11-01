package com.company.leadershipintelligence.domain.service.penalties;

import com.company.leadershipintelligence.domain.enums.PenaltyStrategyType;
import com.company.leadershipintelligence.domain.rule.StrategyConfig;
import com.company.leadershipintelligence.domain.strategy.PenaltyCalculation;
import com.company.leadershipintelligence.domain.strategy.PenaltyContext;
import com.company.leadershipintelligence.domain.strategy.PenaltyStrategy;

public class FixedPenaltyStrategy implements PenaltyStrategy {

    @Override
    public PenaltyStrategyType supportedType() {
        return PenaltyStrategyType.FIXED;
    }

    @Override
    public PenaltyCalculation calculate(PenaltyContext context, StrategyConfig configuration) {
        StrategyConfig.Fixed fixed = (StrategyConfig.Fixed) configuration;
        return PenaltyCalculation.calculated(fixed.deduction().value(), null, null);
    }
}
