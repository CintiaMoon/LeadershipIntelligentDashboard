package com.company.leadershipintelligence.api.request;

import jakarta.validation.constraints.Size;

/** Optional body of {@code POST /score/recalculate} (decision D21): a configuration version id to use instead of the effective one. */
public record RecalculateRequest(@Size(max = 64) String configurationVersion) {
}
