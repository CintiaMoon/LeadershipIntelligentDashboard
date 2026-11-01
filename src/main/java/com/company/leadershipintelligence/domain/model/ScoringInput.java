package com.company.leadershipintelligence.domain.model;

/**
 * The answer to one question (specv3.md Section 4.3). The question type and normalization
 * configuration are resolved from configuration, never from this record. A null answer means
 * missing data, not zero or false.
 */
public record ScoringInput(String questionId, Object answer) {

    public ScoringInput {
        if (questionId == null || questionId.isBlank()) {
            throw new IllegalArgumentException("questionId must not be blank");
        }
    }

    public boolean isMissing() {
        return answer == null;
    }
}
