package com.company.leadershipintelligence.domain.valueobject;

/** Identity of an immutable assessment snapshot (specv3.md Section 4.1). */
public record AssessmentSnapshotId(String value) {

    public AssessmentSnapshotId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AssessmentSnapshotId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
