package com.company.leadershipintelligence.api.security;

import org.springframework.security.access.AccessDeniedException;

/**
 * The administrator of the platform (decision D46). It belongs to no tenant: its caller carries no company, so it can reach nothing
 * tenant-scoped, and the tenant roles can reach nothing here. It is deliberately not a value of {@link Role}: the rules that let
 * "every role" read tenant data are built from that enum, and this role must never be part of them.
 */
public final class PlatformRole {

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private PlatformRole() {
    }

    /** Defence in depth behind the request rule: the controller checks the caller itself as well. */
    public static void require(AuthenticatedCaller caller) {
        if (!caller.hasRole(PLATFORM_ADMIN)) {
            throw new AccessDeniedException("only a platform administrator may do this");
        }
    }

    /** Who did it, for the audit: the verified subject, never anything from the request. */
    public static String actor(AuthenticatedCaller caller) {
        return caller.subject() == null ? "unknown" : caller.subject();
    }
}
