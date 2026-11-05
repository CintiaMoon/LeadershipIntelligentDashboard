package com.company.leadershipintelligence.repository.persistence.entity;

import com.company.leadershipintelligence.domain.identity.MembershipStatus;

import java.util.Set;
import java.util.UUID;

/** A stored membership with its roles and organizations. {@code userId} is null for an invitation to a person without an account. */
public record MembershipRow(UUID id, String tenantId, String tenantName, UUID userId, String invitedEmail, MembershipStatus status,
                            Set<String> roles, Set<String> organizationIds) {
}
