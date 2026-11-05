package com.company.leadershipintelligence.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The body of {@code POST /members} (decision D72). The company is the one the caller belongs to. No organizations means every
 * organization the inviter can reach.
 */
public record InviteMemberRequest(
        @NotBlank @Size(max = 254) String email,
        @NotEmpty @Size(max = 6) List<@NotBlank @Size(max = 24) String> roles,
        @Size(max = 100) List<@Size(max = 64) String> organizationIds) {
}
