package com.company.leadershipintelligence.repository.persistence.entity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A stored API token without its secret: the hash never leaves the store. */
public record ApiTokenRow(UUID id, String tenantId, UUID createdBy, String createdByEmail, String name, String role, String prefix,
                          Set<String> organizationIds, Instant expiresAt, Instant revokedAt, Instant lastUsedAt, Instant createdAt) {
}
