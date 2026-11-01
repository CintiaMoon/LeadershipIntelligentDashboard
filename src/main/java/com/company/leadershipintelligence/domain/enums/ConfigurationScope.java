package com.company.leadershipintelligence.domain.enums;

/** Configuration layers, lowest to highest precedence (specv3.md Section 23.2). */
public enum ConfigurationScope {
    PLATFORM_DEFAULT(0),
    TEMPLATE(1),
    ORGANIZATION(2),
    TENANT(3);

    private final int precedence;

    ConfigurationScope(int precedence) {
        this.precedence = precedence;
    }

    /** Higher wins. */
    public int precedence() {
        return precedence;
    }

    /** Platform defaults belong to no tenant; every other layer does. */
    public boolean tenantOwned() {
        return this != PLATFORM_DEFAULT;
    }
}
