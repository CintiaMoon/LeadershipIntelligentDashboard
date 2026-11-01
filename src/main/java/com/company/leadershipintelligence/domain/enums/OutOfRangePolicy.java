package com.company.leadershipintelligence.domain.enums;

/** Explicit policy for values outside the configured range (specv3.md Section 8.5). No silent clamping. */
public enum OutOfRangePolicy {
    REJECT,
    CLAMP
}
