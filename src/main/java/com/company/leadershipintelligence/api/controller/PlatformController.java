package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.api.request.CreateTenantRequest;
import com.company.leadershipintelligence.api.response.TenantListResponse;
import com.company.leadershipintelligence.api.response.TenantResponse;
import com.company.leadershipintelligence.api.security.PlatformRole;
import com.company.leadershipintelligence.service.TenantAdministrationService;
import com.company.leadershipintelligence.service.query.TenantView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform administration (decision D46): creating and listing tenants. These are the only endpoints that are not scoped to
 * a tenant, so they take no tenant from the token, and only the platform administrator role reaches them (the request rule
 * and this controller both check it). The tenant roles can reach nothing here. Names and ids are never written to the logs.
 */
@RestController
@RequestMapping("/api/v1/platform")
@Tag(name = "Platform")
public class PlatformController {

    private final TenantAdministrationService tenants;

    public PlatformController(TenantAdministrationService tenants) {
        this.tenants = tenants;
    }

    @Operation(summary = "Every tenant (platform administrators only; the read is audited)")
    @GetMapping("/tenants")
    public TenantListResponse list(@AuthenticationPrincipal Jwt jwt) {
        PlatformRole.require(jwt);
        return new TenantListResponse(tenants.list(PlatformRole.actor(jwt)).stream().map(PlatformController::response).toList());
    }

    @Operation(summary = "Create a tenant (platform administrators only; audited)")
    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTenantRequest request) {
        PlatformRole.require(jwt);
        return response(tenants.create(PlatformRole.actor(jwt), request.id(), request.name()));
    }

    private static TenantResponse response(TenantView view) {
        return new TenantResponse(view.id(), view.name(), view.createdAt());
    }
}
