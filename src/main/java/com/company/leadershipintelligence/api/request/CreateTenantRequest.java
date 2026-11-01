package com.company.leadershipintelligence.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /platform/tenants} (decision D46): a permanent id and a display name, nothing else. Unknown fields
 * are rejected like everywhere else. The service applies the stricter rule for the id.
 */
public record CreateTenantRequest(
        @NotBlank @Size(max = 63) String id,
        @NotBlank @Size(max = 200) String name) {
}
