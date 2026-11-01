package com.company.leadershipintelligence.domain.enums;

/**
 * Outcome of normalizing one input (specv3.md Sections 4.4, 8.10). Only statuses that carry a
 * numeric value may be paired with a normalized value.
 */
public enum NormalizationStatus {
    NORMALIZED(true),
    NEUTRAL_APPLIED(true),
    MISSING_IGNORED(false),
    MISSING_RISK_SIGNAL(false),
    SIGNALS_ONLY(false);

    private final boolean carriesValue;

    NormalizationStatus(boolean carriesValue) {
        this.carriesValue = carriesValue;
    }

    public boolean carriesValue() {
        return carriesValue;
    }
}
