package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.valueobject.ConfidenceValue;

/**
 * Evidence gate for a pattern (specv3.md Section 13.1). {@code minimumSignals} is the minimum number of
 * independent (distinct correlation group) supporting operands; {@code minimumConfidence} is in [0, 1].
 */
public record EvidenceRequirements(int minimumSignals, ConfidenceValue minimumConfidence) {

    public EvidenceRequirements {
        ConditionChecks.require(minimumSignals >= 1, "minimumSignals must be at least 1");
        ConditionChecks.require(minimumConfidence != null, "minimumConfidence is required");
    }
}
