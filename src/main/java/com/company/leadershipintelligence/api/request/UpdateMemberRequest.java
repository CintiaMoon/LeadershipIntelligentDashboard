package com.company.leadershipintelligence.api.request;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The body of {@code PATCH /members/{id}} (decision D72). A field that is absent is left alone; an empty {@code organizationIds}
 * removes the restriction. {@code status} is ACTIVE or SUSPENDED.
 */
public record UpdateMemberRequest(
        @Size(max = 6) List<@Size(max = 24) String> roles,
        @Size(max = 100) List<@Size(max = 64) String> organizationIds,
        @Size(max = 16) String status) {
}
