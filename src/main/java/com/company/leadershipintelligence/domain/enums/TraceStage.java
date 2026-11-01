package com.company.leadershipintelligence.domain.enums;

/** Ordered stages of the explanation trace (decision D24 plus the leading normalization and signal stages, specv3.md Section 19.1). */
public enum TraceStage {
    NORMALIZATION,
    SIGNALS,
    RAW_DIMENSIONS,
    IMBALANCES,
    PATTERNS,
    PENALTIES,
    ADJUSTED_DIMENSIONS,
    OVERALL
}
