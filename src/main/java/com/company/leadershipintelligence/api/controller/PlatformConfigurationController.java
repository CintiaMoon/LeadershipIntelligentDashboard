package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.api.security.PlatformRole;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.configuration.ConfigurationReviewService;
import com.company.leadershipintelligence.service.configuration.Manager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.company.leadershipintelligence.api.security.AuthenticatedCaller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuration management of the platform default (decision D52), for the platform administrator only: the request rule and this
 * controller both check the role. The default applies to every tenant, so every change goes through the same second-person approval
 * and fixtures gate as any other.
 */
@RestController
@RequestMapping("/api/v1/platform/configuration")
@Tag(name = "Platform configuration")
public class PlatformConfigurationController extends ConfigurationApi {

    public PlatformConfigurationController(ConfigurationReviewService review, ObjectMapper json) {
        super(review, json);
    }

    @Override
    protected Manager manager(AuthenticatedCaller caller) {
        PlatformRole.require(caller);
        return Manager.platform(PlatformRole.actor(caller));
    }

    @Override
    protected ConfigurationTarget target(Manager manager, String scope, String organizationId) {
        return ConfigurationTarget.platform();
    }
}
