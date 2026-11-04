package com.company.leadershipintelligence.api.response;

import java.util.List;
import java.util.UUID;

/**
 * The signed-in user (decisions D65, D69). {@code companies} lists the companies the user may open; it is empty until company
 * memberships exist (a later phase of the same proposal), and never lists a company without an ACTIVE membership.
 */
public record AccountResponse(UUID id, String email, String displayName, boolean platformAdmin, List<CompanyRef> companies) {

    public record CompanyRef(String id, String name) {
    }
}
