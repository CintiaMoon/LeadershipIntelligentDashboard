package com.company.leadershipintelligence.service.command;

import com.company.leadershipintelligence.domain.enums.ConfigurationScope;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;

/** The configuration slot a draft belongs to: platform default, or a template, organization or tenant layer. */
public record ConfigurationTarget(ConfigurationScope scope, TenantId tenantId, String scopeId) {

    public ConfigurationTarget {
        if (scope == null) {
            throw new IllegalArgumentException("scope is required");
        }
        if (scope.tenantOwned() != (tenantId != null)) {
            throw new IllegalArgumentException(scope + (scope.tenantOwned() ? " needs a tenant" : " has no tenant"));
        }
        boolean needsScopeId = scope == ConfigurationScope.TEMPLATE || scope == ConfigurationScope.ORGANIZATION;
        if (needsScopeId != (scopeId != null && !scopeId.isBlank())) {
            throw new IllegalArgumentException(scope + (needsScopeId ? " needs a scope id" : " has no scope id"));
        }
    }

    public static ConfigurationTarget platform() {
        return new ConfigurationTarget(ConfigurationScope.PLATFORM_DEFAULT, null, null);
    }

    public static ConfigurationTarget tenant(TenantId tenantId) {
        return new ConfigurationTarget(ConfigurationScope.TENANT, tenantId, null);
    }

    public static ConfigurationTarget organization(TenantId tenantId, OrganizationId organizationId) {
        return new ConfigurationTarget(ConfigurationScope.ORGANIZATION, tenantId, organizationId.value());
    }

    public static ConfigurationTarget template(TenantId tenantId, String templateId) {
        return new ConfigurationTarget(ConfigurationScope.TEMPLATE, tenantId, templateId);
    }
}
