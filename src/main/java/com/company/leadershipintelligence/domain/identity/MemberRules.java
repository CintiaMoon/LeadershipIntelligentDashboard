package com.company.leadershipintelligence.domain.identity;

import java.util.List;
import java.util.Set;

/**
 * Who may manage whose membership (decision D72). Pure rules, no storage.
 *
 * <ul>
 *   <li>An OWNER may grant and remove every role.</li>
 *   <li>An ADMIN may manage ENGINEERING_LEADER, ANALYST, VIEWER and RESPONDENT, but not OWNER or ADMIN: it cannot grant them and
 *       cannot change, suspend or remove someone who holds one.</li>
 *   <li>Nobody else manages members.</li>
 *   <li>A membership holds tenant roles, or RESPONDENT on its own (the respondent role is kept apart from the tenant roles).</li>
 * </ul>
 *
 * Nobody changes their own membership and a company keeps one ACTIVE OWNER; those need the stored state and are checked by the service.
 */
public final class MemberRules {

    public static final String RESPONDENT = "RESPONDENT";
    /** Every role a membership may hold. */
    public static final List<String> ROLES = List.of("OWNER", "ADMIN", "ENGINEERING_LEADER", "ANALYST", "VIEWER", RESPONDENT);

    private MemberRules() {
    }

    /** True when the set is not empty, every name is a known role, and RESPONDENT is not mixed with tenant roles. */
    public static boolean validRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty() || !ROLES.containsAll(roles)) {
            return false;
        }
        return !roles.contains(RESPONDENT) || roles.size() == 1;
    }

    /** True when someone who holds {@code actorRoles} may give a membership these roles. */
    public static boolean mayGrant(Set<String> actorRoles, Set<String> roles) {
        if (actorRoles.contains("OWNER")) {
            return true;
        }
        return actorRoles.contains("ADMIN") && !roles.contains("OWNER") && !roles.contains("ADMIN");
    }

    /** True when someone who holds {@code actorRoles} may change, suspend or remove a membership that holds {@code targetRoles}. */
    public static boolean mayManage(Set<String> actorRoles, Set<String> targetRoles) {
        return mayGrant(actorRoles, targetRoles);
    }
}
