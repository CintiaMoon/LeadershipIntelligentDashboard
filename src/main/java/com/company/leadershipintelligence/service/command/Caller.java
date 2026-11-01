package com.company.leadershipintelligence.service.command;

import com.company.leadershipintelligence.domain.valueobject.TenantId;

/**
 * Who is acting, resolved by the API layer from the security context (never from a request body). A tenant
 * caller reaches only its own tenant's configuration; the platform operator reaches only platform defaults
 * (specv3.md Section 25.3).
 */
public record Caller(TenantId tenantId, String actor) {

    public Caller {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor is required");
        }
    }

    public static Caller platform(String actor) {
        return new Caller(null, actor);
    }

    public static Caller tenant(TenantId tenantId, String actor) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant id is required");
        }
        return new Caller(tenantId, actor);
    }

    public boolean isPlatform() {
        return tenantId == null;
    }
}
