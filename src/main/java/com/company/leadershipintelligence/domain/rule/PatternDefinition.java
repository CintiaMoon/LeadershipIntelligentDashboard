package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.PatternCategory;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;

import java.util.List;

/**
 * A configured pattern (specv3.md Section 13.1). Catalog IDs (TH-, DS-, TR-, LL-, XD-) are stable and are
 * not executable until conditions and thresholds are configured and validated. A pattern carries no
 * penalty; {@code penaltyEligible} only says a later stage may consider one.
 */
public record PatternDefinition(
        String patternId,
        String code,
        int version,
        PatternCategory category,
        Severity severity,
        boolean enabled,
        Condition condition,
        EvidenceRequirements evidence,
        boolean penaltyEligible,
        List<String> actionKeys) {

    public PatternDefinition {
        if (patternId == null || patternId.isBlank() || code == null || code.isBlank() || version < 1
                || category == null || severity == null || condition == null || evidence == null) {
            throw new ScoringException(ScoringErrorCode.INVALID_PATTERN_CONFIGURATION,
                    "pattern id, code, version, category, severity, condition and evidence are required");
        }
        actionKeys = actionKeys == null ? List.of() : List.copyOf(actionKeys);
    }
}
