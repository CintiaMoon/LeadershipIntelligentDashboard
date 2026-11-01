package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.EvaluationOutcome;
import com.company.leadershipintelligence.domain.enums.PenaltyReason;
import com.company.leadershipintelligence.domain.enums.PenaltyStrategyType;
import com.company.leadershipintelligence.domain.enums.PenaltyTarget;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.rule.PenaltyDefinition;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;

import java.math.BigDecimal;
import java.util.List;

/**
 * Output of the penalty evaluation stage, before stacking. A CANDIDATE has a positive candidate deduction
 * (capped by the individual maximum and rounded to 2 decimals); other outcomes carry none.
 */
public record PenaltyEvaluation(
        PenaltyDefinition definition,
        EvaluationOutcome outcome,
        PenaltyReason reason,
        Severity triggerSeverity,
        String scoreBase,
        BigDecimal scoreBaseValue,
        BigDecimal calculatedDeduction,
        PenaltyAmount candidateDeduction,
        List<String> evidenceRefs) {

    public PenaltyEvaluation {
        if ((outcome == EvaluationOutcome.CANDIDATE) != (candidateDeduction != null && candidateDeduction.value().signum() > 0)) {
            throw new IllegalArgumentException("a positive candidate deduction exists exactly for CANDIDATE outcomes");
        }
        evidenceRefs = List.copyOf(evidenceRefs);
    }

    public String penaltyId() {
        return definition.penaltyId();
    }

    public PenaltyTarget target() {
        return definition.target();
    }

    public PenaltyStrategyType strategyType() {
        return definition.strategy().type();
    }
}
