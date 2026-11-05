package com.company.leadershipintelligence.repository.persistence.entity;

import com.company.leadershipintelligence.domain.identity.MembershipStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A membership as the member list shows it: who (the account's email and name, or the invited email while there is no account yet),
 * their status, roles and organizations. Never carries a password hash or a token.
 */
public record MemberRow(UUID id, String tenantId, UUID userId, String email, String displayName, MembershipStatus status, Set<String> roles,
                        Set<String> organizationIds, String invitedBy, Instant createdAt, Instant updatedAt) {
}
