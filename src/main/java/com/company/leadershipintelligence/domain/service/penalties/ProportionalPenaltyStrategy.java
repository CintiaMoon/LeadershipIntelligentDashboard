package com.company.leadershipintelligence.domain.service.penalties;

import com.company.leadershipintelligence.domain.enums.PenaltyReason;
import com.company.leadershipintelligence.domain.enums.PenaltyStrategyType;
import com.company.leadershipintelligence.domain.rule.StrategyConfig;
import com.company.leadershipintelligence.domain.strategy.PenaltyCalculation;
import com.company.leadershipintelligence.domain.strategy.PenaltyContext;
import com.company.leadershipintelligence.domain.strategy.PenaltyStrategy;

import java.math.BigDecimal;
import java.util.Optional;

public class ProportionalPenaltyStrategy implements PenaltyStrategy {

    @Override
    public PenaltyStrategyType supportedType() {
        return PenaltyStrategyType.PROPORTIONAL;
    }

    @Override
    public PenaltyCalculation calculate(PenaltyContext context, StrategyConfig configuration) {
        StrategyConfig.Proportional config = (StrategyConfig.Proportional) configuration;
        Optional<BigDecimal> base = ScoreBases.resolve(config.base(), context.evaluation());
        if (base.isEmpty()) {
            return PenaltyCalculation.missing(PenaltyReason.MISSING_SCORE_BASE, config.base().describe());
        }
        BigDecimal baseValue = base.get().max(BigDecimal.ZERO);
        return PenaltyCalculation.calculated(baseValue.multiply(config.rate()), config.base().describe(), base.get());
    }
}
