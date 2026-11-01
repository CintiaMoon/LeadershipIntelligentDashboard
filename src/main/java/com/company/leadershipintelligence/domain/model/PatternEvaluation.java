package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.ConfidenceBand;
import com.company.leadershipintelligence.domain.enums.PatternCategory;
import com.company.leadershipintelligence.domain.enums.PatternReason;
import com.company.leadershipintelligence.domain.enums.PatternStatus;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.valueobject.ConfidenceValue;

import java.util.List;

/**
 * Result of evaluating one pattern (specv3.md Sections 13.6, 14). Exactly one status with a reason. Severity
 * is the configured impact and is never derived from confidence. Evidence and confidence exist only when
 * the condition was satisfied (MATCHED or INELIGIBLE). {@code penaltyEligible} is true only for MATCHED
 * patterns whose definition allows penalties; the pattern itself carries no deduction.
 */
public record PatternEvaluation(
        String patternId,
        String code,
        int version,
        PatternCategory category,
        Severity severity,
        PatternStatus status,
        PatternReason reason,
        ConditionResult conditionResult,
        List<String> missingOperands,
        List<PatternEvidence> evidence,
        ConfidenceValue confidence,
        ConfidenceBand confidenceBand,
        ConfidenceBreakdown confidenceBreakdown,
        boolean penaltyEligible,
        List<String> actionKeys) {

    public PatternEvaluation {
        boolean hasEvidence = status == PatternStatus.MATCHED || status == PatternStatus.INELIGIBLE;
        if (hasEvidence != (confidence != null)) {
            throw new IllegalArgumentException("confidence exists exactly for MATCHED or INELIGIBLE patterns");
        }
        if (!hasEvidence && !evidence.isEmpty()) {
            throw new IllegalArgumentException("evidence exists only for MATCHED or INELIGIBLE patterns");
        }
        if (penaltyEligible && status != PatternStatus.MATCHED) {
            throw new IllegalArgumentException("only a MATCHED pattern can be penalty eligible");
        }
        missingOperands = List.copyOf(missingOperands);
        evidence = List.copyOf(evidence);
        actionKeys = List.copyOf(actionKeys);
    }
}
