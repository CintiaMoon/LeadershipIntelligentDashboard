package com.company.leadershipintelligence.api.security;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.service.command.OrganizationScope;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * The authenticated caller as the services read it (decisions D19, D20, D69; specv3.md Section 25.1). The tenant is the company of the
 * caller's membership or API token and nothing else: it is never taken from a body, header value or path. Unknown role names are
 * ignored. {@code allOrganizations} means the caller is not limited to a list of organizations (a membership or token without one):
 * it may name an organization that does not exist yet, and {@code organizationIds} then holds the ones that exist.
 */
public record CurrentUser(TenantId tenantId, String subject, Set<Role> roles, Set<String> organizationIds, boolean allOrganizations) {

    public static CurrentUser from(AuthenticatedCaller caller) {
        String tenant = caller.tenantId();
        if (tenant == null || tenant.isBlank()) {
            throw new ScoringException(ScoringErrorCode.TENANT_CONTEXT_MISSING, "the caller belongs to no company");
        }
        return new CurrentUser(new TenantId(tenant), caller.subject(), roles(caller), caller.organizationIds(), caller.allOrganizations());
    }

    private static Set<Role> roles(AuthenticatedCaller caller) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String name : caller.roles()) {
            Arrays.stream(Role.values()).filter(r -> r.name().equals(name)).findFirst().ifPresent(roles::add);
        }
        return roles;
    }

    public boolean hasAnyRole(Role... candidates) {
        return Arrays.stream(candidates).anyMatch(roles::contains);
    }

    public OrganizationScope scope() {
        return allOrganizations ? new OrganizationScope(organizationIds, true) : OrganizationScope.of(organizationIds);
    }

    public String actor() {
        return subject == null ? "unknown" : subject;
    }
}
