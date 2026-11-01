package com.company.leadershipintelligence.domain.enums;

/** Allowed penalty targets (specv3.md Section 15.1). */
public enum PenaltyTarget {
    TEAM_HEALTH,
    DELIVERY_STABILITY,
    TECHNICAL_RISK,
    LEADERSHIP_LOAD,
    OVERALL;

    /** The dimension this target refers to, or empty for OVERALL. */
    public java.util.Optional<ScoringDimension> dimension() {
        return this == OVERALL ? java.util.Optional.empty() : java.util.Optional.of(ScoringDimension.valueOf(name()));
    }
}
