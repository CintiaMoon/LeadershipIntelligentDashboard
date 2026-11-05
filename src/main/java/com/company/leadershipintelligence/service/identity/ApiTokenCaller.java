package com.company.leadershipintelligence.service.identity;

import java.util.Set;
import java.util.UUID;

/** Whom a valid API token stands for on this request: its company, its one role and the organizations it is limited to. */
public record ApiTokenCaller(UUID tokenId, String tenantId, String role, Set<String> organizationIds) {

    /** The subject recorded in audits for what this token does, so it can be told apart from a person. */
    public String subject() {
        return "api-token:" + tokenId;
    }
}
