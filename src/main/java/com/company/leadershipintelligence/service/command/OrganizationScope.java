package com.company.leadershipintelligence.service.command;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;

import java.util.Collection;
import java.util.Set;

/**
 * The organizations the caller may reach (decision D20), taken from the authenticated user's {@code org_ids}.
 * A request that touches another organization is refused. {@link #unrestricted()} is for internal callers only.
 */
public record OrganizationScope(Set<String> organizationIds, boolean everything) {

    public OrganizationScope {
        organizationIds = Set.copyOf(organizationIds);
    }

    public static OrganizationScope unrestricted() {
        return new OrganizationScope(Set.of(), true);
    }

    public static OrganizationScope of(Collection<String> organizationIds) {
        return new OrganizationScope(Set.copyOf(organizationIds), false);
    }

    public void require(String organizationId) {
        if (!everything && !organizationIds.contains(organizationId)) {
            throw new ScoringException(ScoringErrorCode.ORGANIZATION_ACCESS_DENIED, "the caller cannot access this organization");
        }
    }
}
