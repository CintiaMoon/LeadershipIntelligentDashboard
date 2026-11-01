package com.company.leadershipintelligence.domain.rule;

/**
 * Overall weights and interpretation (specv3.md Sections 17, 18). {@link #none()} means not configured;
 * the dimension interpretation is optional (without it dimensions get no label).
 */
public record OverallScoringConfig(OverallWeights weights, InterpretationConfig lisInterpretation,
                                   InterpretationConfig dimensionInterpretation) {

    public static OverallScoringConfig none() {
        return new OverallScoringConfig(null, null, null);
    }

    public boolean isConfigured() {
        return weights != null && lisInterpretation != null;
    }
}
