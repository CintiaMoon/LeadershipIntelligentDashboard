package com.company.leadershipintelligence.api.security;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.service.command.OrganizationScope;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The authenticated caller, read only from the verified token (decisions D19, D20; specv3.md Section 25.1). The
 * tenant is the {@code tenant_id} claim and nothing else: it is never taken from a body, header or path. Unknown
 * role names are ignored.
 */
public record CurrentUser(TenantId tenantId, String subject, Set<Role> roles, Set<String> organizationIds, boolean allOrganizations) {

    public static final String TENANT_CLAIM = "tenant_id";
    public static final String ROLES_CLAIM = "roles";
    public static final String ORGANIZATIONS_CLAIM = "org_ids";
    /**
     * Set by the application itself (never by a client) for a caller who is not limited to some organizations of the company: a
     * membership or API token without an organization list. It may name an organization that does not exist yet. Only honoured on the
     * internal caller object, so a verified external token that happens to carry the claim gains nothing.
     */
    public static final String ALL_ORGANIZATIONS_CLAIM = "org_all";
    public static final String INTERNAL_TOKEN_VALUE = "internal";

    public static CurrentUser from(Jwt jwt) {
        String tenant = jwt.getClaimAsString(TENANT_CLAIM);
        if (tenant == null || tenant.isBlank()) {
            throw new ScoringException(ScoringErrorCode.TENANT_CONTEXT_MISSING, "the token carries no tenant");
        }
        boolean all = INTERNAL_TOKEN_VALUE.equals(jwt.getTokenValue()) && Boolean.TRUE.equals(jwt.getClaim(ALL_ORGANIZATIONS_CLAIM));
        return new CurrentUser(new TenantId(tenant), jwt.getSubject(), roles(jwt), organizations(jwt), all);
    }

    static Set<Role> roles(Jwt jwt) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        List<String> claim = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (claim != null) {
            for (String name : claim) {
                Arrays.stream(Role.values()).filter(r -> r.name().equals(name)).findFirst().ifPresent(roles::add);
            }
        }
        return roles;
    }

    private static Set<String> organizations(Jwt jwt) {
        List<String> claim = jwt.getClaimAsStringList(ORGANIZATIONS_CLAIM);
        return claim == null ? Set.of() : Set.copyOf(claim);
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
