package com.company.leadershipintelligence.domain.enums;

/** Exactly one status per evaluated pattern (specv3.md Section 13.6). */
public enum PatternStatus {
    MATCHED,
    NOT_MATCHED,
    INELIGIBLE,
    NOT_EVALUATED
}
