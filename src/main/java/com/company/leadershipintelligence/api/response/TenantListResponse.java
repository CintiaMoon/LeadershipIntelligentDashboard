package com.company.leadershipintelligence.api.response;

import java.util.List;

/** Every tenant, oldest first. Only a platform administrator receives it. */
public record TenantListResponse(List<TenantResponse> items) {
}
