package com.company.leadershipintelligence.api.security;

import java.util.Collection;
import java.util.Set;

/** Builds the caller of a request that the application itself authenticated (a session with a membership, an API token). */
final class CallerFactory {

    private CallerFactory() {
    }

    static CallerAuthentication of(String subject, String email, String tenantId, Collection<String> roles, Collection<String> organizations, boolean allOrganizations) {
        return new CallerAuthentication(new AuthenticatedCaller(subject, email, tenantId, Set.copyOf(roles), Set.copyOf(organizations), allOrganizations));
    }
}
