package com.company.leadershipintelligence.api.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Bodies of the configuration management endpoints (decisions D52 to D58). Unknown fields are rejected. */
public final class ConfigurationRequests {

    private ConfigurationRequests() {
    }

    /**
     * A new draft. {@code scope} is TENANT or ORGANIZATION (ignored on the platform path, where it is always the platform default);
     * {@code organizationId} names the organization of an ORGANIZATION layer. Without a {@code document} the draft is a copy of the
     * layer's active version.
     */
    public record CreateDraft(@Size(max = 32) String scope, @Size(max = 64) String organizationId, JsonNode document) {
    }

    public record Approve(@NotBlank @Size(max = 4000) String note, Instant effectiveFrom, @Size(max = 2000) String calibrationReference,
                          @Size(max = 4000) String knownLimitations) {
    }

    public record Reject(@NotBlank @Size(max = 4000) String comment) {
    }
}
