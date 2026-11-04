package com.company.leadershipintelligence.repository.persistence.entity;

import com.company.leadershipintelligence.domain.identity.AccountStatus;

import java.time.Instant;
import java.util.UUID;

/** A stored account. The password hash is carried only to the code that checks a password; it is never put in a view or a log. */
public record UserRow(UUID id, String email, String displayName, String passwordHash, AccountStatus status,
                      Instant emailVerifiedAt, boolean platformAdmin, Instant passwordChangedAt) {
}
