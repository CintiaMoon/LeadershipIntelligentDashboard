package com.company.leadershipintelligence.domain.enums;

/** Penalty strategies (specv3.md Section 15.3). DIMENSION and OVERALL are targets, not strategies. */
public enum PenaltyStrategyType {
    FIXED,
    PROPORTIONAL,
    SEVERITY_BASED,
    THRESHOLD_BASED,
    CONDITIONAL
}
