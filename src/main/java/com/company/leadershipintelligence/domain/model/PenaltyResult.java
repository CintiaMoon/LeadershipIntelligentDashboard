package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.PenaltyReason;
import com.company.leadershipintelligence.domain.enums.PenaltyStatus;
import com.company.leadershipintelligence.domain.enums.StackingPolicy;
import com.company.leadershipintelligence.domain.rule.PenaltyDefinition;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;

import java.math.BigDecimal;
import java.util.List;

/**
 * Final record of one penalty with exactly one terminal status (specv3.md Sections 16.4, 16.5). For a
 * candidate, {@code effective + suppressed = candidate}. APPLIED means effective &gt; 0 (possibly less than
 * the candidate when a cumulative cap trimmed it). SUPPRESSED means effective is 0 and the amount is
 * explained by a policy, a winner (where one exists) and a reason.
 */
public record PenaltyResult(
        PenaltyDefinition definition,
        PenaltyStatus status,
        PenaltyReason reason,
        PenaltyAmount candidateDeduction,
        PenaltyAmount effectiveDeduction,
        PenaltyAmount suppressedAmount,
        String winningPenaltyId,
        StackingPolicy policy,
        String scoreBase,
        BigDecimal scoreBaseValue,
        List<String> evidenceRefs) {

    public PenaltyResult {
        BigDecimal candidate = candidateDeduction.value();
        BigDecimal effective = effectiveDeduction.value();
        BigDecimal suppressed = suppressedAmount.value();
        if (candidate.compareTo(effective.add(suppressed)) != 0) {
            throw new IllegalArgumentException("effective + suppressed must equal the candidate deduction");
        }
        switch (status) {
            case APPLIED -> {
                if (effective.signum() <= 0) {
                    throw new IllegalArgumentException("APPLIED needs a positive effective deduction");
                }
            }
            case SUPPRESSED -> {
                if (effective.signum() != 0 || suppressed.signum() <= 0) {
                    throw new IllegalArgumentException("SUPPRESSED needs a zero effective and a positive suppressed amount");
                }
            }
            case INELIGIBLE, NOT_TRIGGERED -> {
                if (candidate.signum() != 0) {
                    throw new IllegalArgumentException(status + " carries no deduction");
                }
            }
        }
        evidenceRefs = List.copyOf(evidenceRefs);
    }

    public String penaltyId() {
        return definition.penaltyId();
    }
}
