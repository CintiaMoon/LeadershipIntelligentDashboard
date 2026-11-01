package com.company.leadershipintelligence.domain.enums;

/** Penalty stacking policies (specv3.md Section 16.1). */
public enum StackingPolicy {
    STACK,
    MAX,
    MAX_PER_CATEGORY,
    PRIORITY,
    CAPPED_STACK,
    EXCLUSIVE_GROUP
}
