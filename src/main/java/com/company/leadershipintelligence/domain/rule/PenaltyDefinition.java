package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.PenaltyTarget;
import com.company.leadershipintelligence.domain.enums.StackingPolicy;
import com.company.leadershipintelligence.domain.valueobject.ConfidenceValue;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;

/**
 * A separately configured scoring consequence (specv3.md Section 15.2). It is triggered by one specific
 * pattern version and is never inferred from a pattern name. Tie-breaking is by {@code priority} (a lower
 * number wins), then {@code ruleOrder}, then penalty id. {@code maximumDeduction} caps this penalty alone;
 * {@code maximumCumulativeDeduction} (CAPPED_STACK only) caps the pool. A penalty applied both to a dimension
 * and to OVERALL for the same pattern needs a written {@code dualApplicationRationale} on both.
 */
public record PenaltyDefinition(
        String penaltyId,
        String code,
        int version,
        String triggerPatternId,
        int triggerPatternVersion,
        PenaltyTarget target,
        StrategyConfig strategy,
        PenaltyAmount maximumDeduction,
        String category,
        String exclusiveGroup,
        int priority,
        int ruleOrder,
        StackingPolicy stackingPolicy,
        PenaltyAmount maximumCumulativeDeduction,
        ConfidenceValue minimumConfidence,
        boolean enabled,
        String dualApplicationRationale,
        String explanationKey) {

    public PenaltyDefinition {
        PenaltyChecks.require(penaltyId != null && !penaltyId.isBlank() && code != null && !code.isBlank() && version >= 1,
                "penalty id, code and version are required");
        PenaltyChecks.require(triggerPatternId != null && !triggerPatternId.isBlank() && triggerPatternVersion >= 1,
                "penalty " + penaltyId + " needs a trigger pattern id and version");
        PenaltyChecks.require(target != null && strategy != null && maximumDeduction != null && stackingPolicy != null
                && minimumConfidence != null, "penalty " + penaltyId + " is missing target, strategy, maximum, stacking policy or minimum confidence");
        PenaltyChecks.require(category != null && !category.isBlank(), "penalty " + penaltyId + " needs a category");
        PenaltyChecks.require(priority >= 0 && ruleOrder >= 0, "priority and ruleOrder must not be negative");
        PenaltyChecks.require((stackingPolicy == StackingPolicy.CAPPED_STACK) == (maximumCumulativeDeduction != null),
                "penalty " + penaltyId + ": a cumulative cap is required for CAPPED_STACK and not allowed otherwise");
        PenaltyChecks.require((stackingPolicy == StackingPolicy.EXCLUSIVE_GROUP) == (exclusiveGroup != null && !exclusiveGroup.isBlank()),
                "penalty " + penaltyId + ": an exclusive group is required for EXCLUSIVE_GROUP and not allowed otherwise");
        PenaltyChecks.require(dualApplicationRationale == null || !dualApplicationRationale.isBlank(),
                "dual application rationale must not be blank");
    }
}
