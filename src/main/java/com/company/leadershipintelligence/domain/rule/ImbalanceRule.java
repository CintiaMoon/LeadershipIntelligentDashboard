package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.EvaluationBasis;
import com.company.leadershipintelligence.domain.enums.GapDirection;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;

import java.math.BigDecimal;

/**
 * A configured gap between two dimensions (specv3.md Sections 12.2, 12.4). The gap is computed per
 * {@code direction} and compared with {@code threshold} using GAP_GT (strictly greater) or GAP_GTE (greater
 * or equal); the boundary is therefore explicit. An imbalance is diagnostic only and never changes a score.
 */
public record ImbalanceRule(
        String ruleId,
        int version,
        ScoringDimension left,
        ScoringDimension right,
        GapDirection direction,
        ConditionOperator gapOperator,
        BigDecimal threshold,
        Severity severity,
        EvaluationBasis basis,
        boolean enabled,
        String explanationKey,
        String recommendationKey) {

    public ImbalanceRule {
        if (ruleId == null || ruleId.isBlank() || version < 1 || left == null || right == null || direction == null
                || gapOperator == null || threshold == null || severity == null || basis == null) {
            throw invalid("rule id, version, both dimensions, direction, operator, threshold, severity and basis are required");
        }
        if (left == right) {
            throw invalid("imbalance " + ruleId + " must compare two different dimensions");
        }
        if (gapOperator != ConditionOperator.GAP_GT && gapOperator != ConditionOperator.GAP_GTE) {
            throw invalid("imbalance " + ruleId + " needs GAP_GT or GAP_GTE");
        }
        if (threshold.signum() < 0 || threshold.compareTo(new BigDecimal("100")) > 0) {
            throw invalid("imbalance " + ruleId + " threshold must be between 0 and 100");
        }
    }

    private static ScoringException invalid(String message) {
        return new ScoringException(ScoringErrorCode.INVALID_IMBALANCE_CONFIGURATION, message);
    }
}
