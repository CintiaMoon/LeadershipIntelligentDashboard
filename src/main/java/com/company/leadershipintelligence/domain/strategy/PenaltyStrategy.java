package com.company.leadershipintelligence.domain.strategy;

import com.company.leadershipintelligence.domain.enums.PenaltyStrategyType;
import com.company.leadershipintelligence.domain.rule.StrategyConfig;

/** Deterministic, independently testable penalty calculation (specv3.md Section 22.2). */
public interface PenaltyStrategy {

    PenaltyStrategyType supportedType();

    PenaltyCalculation calculate(PenaltyContext context, StrategyConfig configuration);
}
