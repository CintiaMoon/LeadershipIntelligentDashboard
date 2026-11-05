package com.company.leadershipintelligence.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * The body of {@code POST /api-tokens} (decision D70). The company is the caller's own and the creator is the caller; neither can
 * be named here. {@code organizationIds} empty or absent means every organization the creator can reach.
 */
public record CreateApiTokenRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 24) String role,
        Instant expiresAt,
        @Size(max = 100) List<@Size(max = 64) String> organizationIds) {
}
