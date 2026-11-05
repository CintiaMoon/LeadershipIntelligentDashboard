package com.company.leadershipintelligence.service.identity;

import java.util.Collection;

/**
 * A change to a membership (decision D72). A field that is null is left alone. {@code organizationIds} empty removes the restriction
 * (every organization of the company). {@code status} is ACTIVE or SUSPENDED.
 */
public record MemberChange(Collection<String> roles, Collection<String> organizationIds, String status) {
}
