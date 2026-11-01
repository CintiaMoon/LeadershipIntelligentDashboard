package com.company.leadershipintelligence.domain.strategy;

import com.company.leadershipintelligence.domain.enums.CalculationOutcome;
import com.company.leadershipintelligence.domain.enums.PenaltyReason;

import java.math.BigDecimal;

/** Unrounded, uncapped result of a strategy. Capping and rounding happen in the evaluation service. */
public record PenaltyCalculation(CalculationOutcome outcome, BigDecimal amount, String scoreBase,
                                 BigDecimal scoreBaseValue, PenaltyReason reason) {

    public static PenaltyCalculation calculated(BigDecimal amount, String scoreBase, BigDecimal scoreBaseValue) {
        return new PenaltyCalculation(CalculationOutcome.CALCULATED, amount, scoreBase, scoreBaseValue, PenaltyReason.ZERO_DEDUCTION);
    }

    public static PenaltyCalculation noDeduction(PenaltyReason reason, String scoreBase, BigDecimal scoreBaseValue) {
        return new PenaltyCalculation(CalculationOutcome.NO_DEDUCTION, null, scoreBase, scoreBaseValue, reason);
    }

    public static PenaltyCalculation missing(PenaltyReason reason, String scoreBase) {
        return new PenaltyCalculation(CalculationOutcome.MISSING_DATA, null, scoreBase, null, reason);
    }
}
