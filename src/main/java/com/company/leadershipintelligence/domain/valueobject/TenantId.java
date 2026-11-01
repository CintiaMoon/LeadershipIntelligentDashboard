package com.company.leadershipintelligence.domain.valueobject;

/** Tenant identity; taken only from the security context (specv3.md Section 25.1). */
public record TenantId(String value) {

    public TenantId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TenantId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
