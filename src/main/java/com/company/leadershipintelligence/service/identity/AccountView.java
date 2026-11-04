package com.company.leadershipintelligence.service.identity;

import java.util.UUID;

/** What the rest of the application may know about an account: never the password hash. */
public record AccountView(UUID id, String email, String displayName, boolean platformAdmin) {
}
