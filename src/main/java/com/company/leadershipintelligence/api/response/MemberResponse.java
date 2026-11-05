package com.company.leadershipintelligence.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A member of the company. {@code email} is the invited address while the person has no account; {@code displayName} is then null. */
public record MemberResponse(UUID id, String email, String displayName, String status, List<String> roles, List<String> organizationIds,
                             Instant createdAt, Instant updatedAt) {
}
