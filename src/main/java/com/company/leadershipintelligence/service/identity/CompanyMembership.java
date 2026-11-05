package com.company.leadershipintelligence.service.identity;

import java.util.Set;

/** What a request may use of a membership (decision D69): the company, the roles held there and the organizations it is limited to. */
public record CompanyMembership(String tenantId, String tenantName, Set<String> roles, Set<String> organizationIds) {
}
