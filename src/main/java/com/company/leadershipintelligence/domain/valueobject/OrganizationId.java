package com.company.leadershipintelligence.domain.valueobject;

/** Organization identity within a tenant. */
public record OrganizationId(String value) {

    public OrganizationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OrganizationId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
