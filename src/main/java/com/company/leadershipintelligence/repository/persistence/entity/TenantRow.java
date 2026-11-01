package com.company.leadershipintelligence.repository.persistence.entity;

import java.time.Instant;

/** A tenant as stored. */
public record TenantRow(String id, String name, Instant createdAt) {
}
