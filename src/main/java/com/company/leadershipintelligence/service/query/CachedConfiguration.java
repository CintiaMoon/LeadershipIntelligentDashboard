package com.company.leadershipintelligence.service.query;

import java.time.Instant;

/** A cached effective configuration and the instant a scheduled change makes it stale (null = no scheduled change). */
public record CachedConfiguration(EffectiveConfiguration configuration, Instant validUntil) {
}
