package com.company.leadershipintelligence.domain.enums;

/** Reason code attached to every pattern status (specv3.md Section 13.6). */
public enum PatternReason {
    CONDITION_SATISFIED,
    CONDITION_NOT_SATISFIED,
    MISSING_SIGNALS,
    PATTERN_DISABLED,
    INSUFFICIENT_INDEPENDENT_SIGNALS,
    CONFIDENCE_BELOW_MINIMUM
}
