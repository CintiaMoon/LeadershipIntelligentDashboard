package com.company.leadershipintelligence.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /platform/tenants} (decisions D46, D69): a permanent id, a display name and, optionally, the email of the
 * company's first owner, who is sent an invitation. Decision D69 makes the owner email part of creating a company; it stays
 * optional until the web application sends it, so a company created without one has no member until an invitation is sent.
 * Unknown fields are rejected like everywhere else. The service applies the stricter rule for the id.
 */
public record CreateTenantRequest(
        @NotBlank @Size(max = 63) String id,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 254) String ownerEmail) {
}
