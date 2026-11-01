package com.company.leadershipintelligence.domain.enums;

/** Three-valued condition outcome. UNKNOWN means required data is missing; it is never read as zero or false. */
public enum TriState {
    TRUE,
    FALSE,
    UNKNOWN
}
