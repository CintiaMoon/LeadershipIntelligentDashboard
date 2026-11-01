package com.company.leadershipintelligence.domain.service.penalties;

import com.company.leadershipintelligence.domain.enums.PenaltyReason;
import com.company.leadershipintelligence.domain.enums.PenaltyStrategyType;
import com.company.leadershipintelligence.domain.rule.StrategyConfig;
import com.company.leadershipintelligence.domain.rule.ThresholdBand;
import com.company.leadershipintelligence.domain.strategy.PenaltyCalculation;
import com.company.leadershipintelligence.domain.strategy.PenaltyContext;
import com.company.leadershipintelligence.domain.strategy.PenaltyStrategy;

import java.math.BigDecimal;
import java.util.Optional;

public class ThresholdBasedPenaltyStrategy implements PenaltyStrategy {

    @Override
    public PenaltyStrategyType supportedType() {
        return PenaltyStrategyType.THRESHOLD_BASED;
    }

    @Override
    public PenaltyCalculation calculate(PenaltyContext context, StrategyConfig configuration) {
        StrategyConfig.ThresholdBased config = (StrategyConfig.ThresholdBased) configuration;
        Optional<BigDecimal> metric = ScoreBases.resolve(config.metric(), context.evaluation());
        if (metric.isEmpty()) {
            return PenaltyCalculation.missing(PenaltyReason.MISSING_SCORE_BASE, config.metric().describe());
        }
        for (ThresholdBand band : config.bands()) {
            if (band.contains(metric.get())) {
                return PenaltyCalculation.calculated(band.deduction().value(), config.metric().describe(), metric.get());
            }
        }
        return PenaltyCalculation.noDeduction(PenaltyReason.BELOW_THRESHOLD, config.metric().describe(), metric.get());
    }
}
