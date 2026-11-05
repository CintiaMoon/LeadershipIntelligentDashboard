package com.company.leadershipintelligence.api.security;

import java.util.Set;

/**
 * Who is calling the API on this request, as the application itself established it: a signed-in person with the active membership of
 * the company named in the request, an API token, or the platform administrator. It is built by the security filters for the one
 * request and read by the controllers; it is never created from anything a client sends in a body, query or header value other than
 * the credential the filters verified.
 *
 * @param subject          the account id, or {@code api-token:<id>} for a token
 * @param email            the verified email of the account (null for a token)
 * @param tenantId         the company, or null for the platform administrator
 * @param roles            the roles held in that company (tenant roles, {@code RESPONDENT}), or {@code PLATFORM_ADMIN}
 * @param organizationIds  the organizations reached; with {@code allOrganizations} the ones that exist now
 * @param allOrganizations not limited to a list: a new organization may be named (organizations are created by first use)
 */
public record AuthenticatedCaller(String subject, String email, String tenantId, Set<String> roles, Set<String> organizationIds, boolean allOrganizations) {

    public AuthenticatedCaller {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        organizationIds = organizationIds == null ? Set.of() : Set.copyOf(organizationIds);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
