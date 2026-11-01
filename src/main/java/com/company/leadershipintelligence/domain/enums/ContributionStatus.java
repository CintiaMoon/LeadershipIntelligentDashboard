package com.company.leadershipintelligence.domain.enums;

/** Fate of one mapped input in a dimension (specv3.md Section 4.5). Excluded inputs carry no contribution. */
public enum ContributionStatus {
    APPLIED,
    NOT_SCORED,
    EXCLUDED_NO_VALUE,
    EXCLUDED_ZERO_WEIGHT
}
