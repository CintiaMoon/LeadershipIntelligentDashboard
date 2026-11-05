package com.company.leadershipintelligence.service.identity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** An API token as it is listed (decision D70). Never carries the secret or its hash; {@code status} is ACTIVE, EXPIRED or REVOKED. */
public record ApiTokenView(UUID id, String name, String role, String prefix, Set<String> organizationIds, UUID createdBy, String createdByEmail,
                           Instant createdAt, Instant expiresAt, Instant lastUsedAt, Instant revokedAt, String status) {
}
