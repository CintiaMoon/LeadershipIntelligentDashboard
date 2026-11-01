package com.company.leadershipintelligence.domain.valueobject;

/** Identity of an immutable scoring run. */
public record ScoringRunId(String value) {

    public ScoringRunId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ScoringRunId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
