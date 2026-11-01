package com.company.leadershipintelligence.api.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * The administrator of the platform (decision D46). It belongs to no tenant: its token carries no {@code tenant_id}, so it
 * can reach nothing tenant-scoped, and the tenant roles can reach nothing here. It is deliberately not a value of
 * {@link Role}: the rules that let "every role" read tenant data are built from that enum, and this role must never be
 * part of them.
 */
public final class PlatformRole {

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private PlatformRole() {
    }

    /** Defence in depth behind the request rule: the controller checks the verified token itself as well. */
    public static void require(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(CurrentUser.ROLES_CLAIM);
        if (roles == null || !roles.contains(PLATFORM_ADMIN)) {
            throw new AccessDeniedException("only a platform administrator may do this");
        }
    }

    /** Who did it, for the audit: the verified subject, never anything from the request. */
    public static String actor(Jwt jwt) {
        return jwt.getSubject() == null ? "unknown" : jwt.getSubject();
    }
}
