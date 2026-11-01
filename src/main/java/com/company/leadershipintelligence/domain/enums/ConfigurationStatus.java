package com.company.leadershipintelligence.domain.enums;

/**
 * Configuration version lifecycle (specv3.md Section 23.5, decision D33). Only DRAFT is editable. A failed
 * validation returns VALIDATING to DRAFT; ACTIVE and PAUSED versions can be archived; ARCHIVED is final.
 */
public enum ConfigurationStatus {
    DRAFT,
    VALIDATING,
    ACTIVE,
    PAUSED,
    ARCHIVED;

    public boolean canTransitionTo(ConfigurationStatus next) {
        return switch (this) {
            case DRAFT -> next == VALIDATING;
            case VALIDATING -> next == DRAFT || next == ACTIVE;
            case ACTIVE -> next == PAUSED || next == ARCHIVED;
            case PAUSED -> next == ACTIVE || next == ARCHIVED;
            case ARCHIVED -> false;
        };
    }

    public boolean editable() {
        return this == DRAFT;
    }

    /** Only ACTIVE versions are used for new scoring runs. */
    public boolean usableForScoring() {
        return this == ACTIVE;
    }
}
