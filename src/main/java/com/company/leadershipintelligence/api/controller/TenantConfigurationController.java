package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.api.security.CurrentUser;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.configuration.ConfigurationReviewService;
import com.company.leadershipintelligence.service.configuration.Manager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuration management for a tenant (decision D52): owners and administrators manage the tenant override and the organization
 * overrides of the organizations in their token; analysts may read. The tenant and the organizations come from the verified token only.
 */
@RestController
@RequestMapping("/api/v1/configuration")
@Tag(name = "Configuration")
public class TenantConfigurationController extends ConfigurationApi {

    public TenantConfigurationController(ConfigurationReviewService review, ObjectMapper json) {
        super(review, json);
    }

    @Override
    protected Manager manager(Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        return new Manager(Caller.tenant(user.tenantId(), user.actor()), user.scope());
    }

    @Override
    protected ConfigurationTarget target(Manager manager, String scope, String organizationId) {
        return tenantTarget(manager, scope, organizationId);
    }
}
