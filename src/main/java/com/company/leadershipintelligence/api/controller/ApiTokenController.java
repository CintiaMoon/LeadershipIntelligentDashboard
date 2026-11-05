package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.api.request.CreateApiTokenRequest;
import com.company.leadershipintelligence.api.response.ApiTokenResponse;
import com.company.leadershipintelligence.api.response.CreatedApiTokenResponse;
import com.company.leadershipintelligence.api.security.CurrentUser;
import com.company.leadershipintelligence.service.identity.ApiTokenService;
import com.company.leadershipintelligence.service.identity.ApiTokenView;
import com.company.leadershipintelligence.service.identity.ClientInfo;
import com.company.leadershipintelligence.service.identity.IssuedApiToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Managing the API tokens of the caller's company (decision D70). Owners and administrators only, and only with a signed-in
 * session: a token cannot create, list or revoke tokens. The company is the one the caller's membership names; the secret is in the
 * answer to the creation and nowhere else.
 */
@RestController
@RequestMapping("/api/v1/api-tokens")
@Tag(name = "API tokens")
public class ApiTokenController {

    private final ApiTokenService tokens;

    public ApiTokenController(ApiTokenService tokens) {
        this.tokens = tokens;
    }

    @Operation(summary = "Create an API token for this company; the secret is returned once")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedApiTokenResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateApiTokenRequest request, HttpServletRequest http) {
        UUID creator = person(jwt);
        IssuedApiToken issued = tokens.create(creator, CurrentUser.from(jwt).tenantId().value(), request.name(), request.role(), request.expiresAt(),
                request.organizationIds(), client(http));
        return new CreatedApiTokenResponse(response(issued.token()), issued.secret());
    }

    @Operation(summary = "The API tokens of this company, newest first (never their secrets)")
    @GetMapping
    public List<ApiTokenResponse> list(@AuthenticationPrincipal Jwt jwt) {
        person(jwt);
        return tokens.list(CurrentUser.from(jwt).tenantId().value()).stream().map(ApiTokenController::response).toList();
    }

    @Operation(summary = "Revoke an API token at once. Revoking a revoked token is not an error")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
        UUID by = person(jwt);
        tokens.revoke(CurrentUser.from(jwt).tenantId().value(), id, by, client(http));
    }

    /** The signed-in person behind the request. A token, or a caller that is not an account of this application, is refused. */
    private static UUID person(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new AccessDeniedException("API tokens are managed with a signed-in session");
        }
    }

    private static ClientInfo client(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    private static ApiTokenResponse response(ApiTokenView view) {
        return new ApiTokenResponse(view.id(), view.name(), view.role(), view.prefix(), List.copyOf(view.organizationIds()), view.createdByEmail(),
                view.createdAt(), view.expiresAt(), view.lastUsedAt(), view.revokedAt(), view.status());
    }
}
