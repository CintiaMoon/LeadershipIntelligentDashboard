package com.company.leadershipintelligence.repository.persistence.entity;

/** An immutable assessment snapshot; a changed answer set is a new snapshot (specv3.md Section 4.1). */
public record SnapshotRow(String id, String tenantId, String assessmentId, int revision, String inputHash) {
}
