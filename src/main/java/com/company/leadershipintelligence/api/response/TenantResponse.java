package com.company.leadershipintelligence.api.response;

import java.time.Instant;

/** A tenant: its permanent id, its display name and when it was created (decision D46). */
public record TenantResponse(String id, String name, Instant createdAt) {
}
