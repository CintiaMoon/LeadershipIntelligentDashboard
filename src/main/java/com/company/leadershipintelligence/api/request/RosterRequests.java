package com.company.leadershipintelligence.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Bodies of the roster endpoints (decision D48). Unknown fields are rejected. */
public final class RosterRequests {

    private RosterRequests() {
    }

    public record CreateTeam(@NotBlank @Size(max = 64) String organizationId, @NotBlank @Size(max = 200) String name) {
    }

    public record RenameTeam(@NotBlank @Size(max = 200) String name) {
    }

    public record CreatePerson(@NotBlank @Size(max = 64) String teamId, @NotBlank @Size(max = 200) String name,
                               @NotBlank @Size(max = 320) String email) {
    }

    /** Replaces the person: people are deactivated, never deleted. */
    public record UpdatePerson(@NotBlank @Size(max = 64) String teamId, @NotBlank @Size(max = 200) String name,
                               @NotBlank @Size(max = 320) String email, boolean active) {
    }
}
