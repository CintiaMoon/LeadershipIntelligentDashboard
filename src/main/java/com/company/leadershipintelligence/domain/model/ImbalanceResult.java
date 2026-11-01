package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.EvaluationBasis;
import com.company.leadershipintelligence.domain.enums.GapDirection;
import com.company.leadershipintelligence.domain.enums.ImbalanceReason;
import com.company.leadershipintelligence.domain.enums.ImbalanceStatus;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.valueobject.ScoreValue;

import java.math.BigDecimal;
import java.util.List;

/**
 * Outcome of one imbalance rule (specv3.md Section 12.3). Values and gap exist only when both dimensions had
 * a valid score; a missing dimension is never read as zero. Severity is the rule's configured severity.
 */
public record ImbalanceResult(
        String ruleId,
        int version,
        ImbalanceStatus status,
        ImbalanceReason reason,
        ScoringDimension left,
        ScoringDimension right,
        ScoreValue leftValue,
        ScoreValue rightValue,
        BigDecimal gap,
        GapDirection direction,
        ConditionOperator gapOperator,
        BigDecimal threshold,
        Severity severity,
        EvaluationBasis basis,
        String explanationKey,
        String recommendationKey,
        List<String> evidenceRefs) {

    public ImbalanceResult {
        boolean evaluated = status != ImbalanceStatus.NOT_EVALUATED;
        if (evaluated != (gap != null && leftValue != null && rightValue != null)) {
            throw new IllegalArgumentException("values and gap exist exactly when the rule was evaluated");
        }
        evidenceRefs = List.copyOf(evidenceRefs);
    }
}
