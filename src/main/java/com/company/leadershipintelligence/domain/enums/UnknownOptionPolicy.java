package com.company.leadershipintelligence.domain.enums;

/** Behavior for an unknown multiple-choice option (specv3.md Section 8.6). Never defaults to zero. */
public enum UnknownOptionPolicy {
    REJECT,
    TREAT_AS_MISSING
}
