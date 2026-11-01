package com.company.leadershipintelligence.service.query;

import java.time.Instant;

/** A tenant as the platform administration exposes it: identity and name only (decision D46). */
public record TenantView(String id, String name, Instant createdAt) {
}
