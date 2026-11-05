package com.company.leadershipintelligence.service.identity;

import java.util.Set;

/**
 * The organizations of one company that a membership or API token reaches. {@code all} means it is not limited to a list: it may
 * also name an organization that does not exist yet (organizations are created by first use), and {@code organizationIds} then holds
 * the organizations that exist now, which is what listings show.
 */
public record OrganizationReach(Set<String> organizationIds, boolean all) {
}
