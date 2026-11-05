package com.company.leadershipintelligence.service.identity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A member of a company as the member list shows it (decision D72). {@code email} is the invited address while there is no account yet. */
public record MemberView(UUID id, String email, String displayName, String status, Set<String> roles, Set<String> organizationIds,
                         String invitedBy, Instant createdAt, Instant updatedAt) {
}
