package com.company.leadershipintelligence.domain.enums;

/** Explicit per-question policy for missing answers (specv3.md Section 8.10). */
public enum MissingDataPolicy {
    IGNORE,
    NEUTRAL,
    RISK_SIGNAL,
    REQUIRED
}
