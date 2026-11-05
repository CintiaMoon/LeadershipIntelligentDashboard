package com.company.leadershipintelligence.service.identity;

import java.util.UUID;

/** The account behind a live session, as it is now: the id, the verified email and whether it is a platform administrator. */
public record SessionAccount(UUID id, String email, boolean platformAdmin) {
}
