package com.company.leadershipintelligence.domain.valueobject;

/** Server-generated assessment identity (decision D14). */
public record AssessmentId(String value) {

    public AssessmentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AssessmentId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
