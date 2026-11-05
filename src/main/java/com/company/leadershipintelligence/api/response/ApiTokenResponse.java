package com.company.leadershipintelligence.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An API token as listed (decision D70): never its secret. {@code status} is ACTIVE, EXPIRED or REVOKED. */
public record ApiTokenResponse(UUID id, String name, String role, String prefix, List<String> organizationIds, String createdBy,
                               Instant createdAt, Instant expiresAt, Instant lastUsedAt, Instant revokedAt, String status) {
}
