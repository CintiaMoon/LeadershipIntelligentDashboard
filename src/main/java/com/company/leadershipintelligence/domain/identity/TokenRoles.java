package com.company.leadershipintelligence.domain.identity;

import java.util.List;
import java.util.Set;

/**
 * Which roles an API token may carry (decision D70). Roles are ranked from the strongest to the weakest as listed; a token carries
 * one role that is never OWNER and is no stronger than the strongest role its creator holds. RESPONDENT and the platform
 * administrator are never available to a token.
 */
public final class TokenRoles {

    /** Strongest first. */
    private static final List<String> RANK = List.of("OWNER", "ADMIN", "ENGINEERING_LEADER", "ANALYST", "VIEWER");

    private TokenRoles() {
    }

    /** Position of a tenant role (0 is the strongest), or -1 for anything else (RESPONDENT, unknown names). */
    public static int rank(String role) {
        return role == null ? -1 : RANK.indexOf(role);
    }

    /** The strongest rank among the roles, or -1 when none is a tenant role. */
    public static int strongest(Set<String> roles) {
        return roles.stream().mapToInt(TokenRoles::rank).filter(r -> r >= 0).min().orElse(-1);
    }

    /** True when a token may carry {@code role} for a creator who holds {@code creatorRoles}. */
    public static boolean mayIssue(Set<String> creatorRoles, String role) {
        int wanted = rank(role);
        int held = strongest(creatorRoles);
        return wanted > 0 && held >= 0 && wanted >= held;
    }

    /** The roles a token may carry, strongest first (never OWNER). */
    public static List<String> issuable() {
        return RANK.subList(1, RANK.size());
    }
}
