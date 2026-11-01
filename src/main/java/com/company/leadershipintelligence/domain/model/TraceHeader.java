package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.ScoreStatus;

/** Versions and validity of the traced run. */
public record TraceHeader(
        String engineVersion,
        String algorithmVersion,
        String configurationVersion,
        String roundingPolicyVersion,
        String confidenceAlgorithmVersion,
        String tieBreakVersion,
        ScoreStatus scoreStatus) {
}
