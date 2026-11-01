package com.company.leadershipintelligence.repository.persistence.entity;

import java.time.Instant;

/** A persisted assessment. The id is server generated (decision D14). */
public record AssessmentRow(String id, String tenantId, String organizationId, String templateId, int templateVersion,
                            String status, Instant completedAt) {
}
