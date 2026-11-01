package com.company.leadershipintelligence.service.query;

import com.company.leadershipintelligence.domain.enums.ConfigurationScope;

import java.util.UUID;

/** One configuration layer that took part in an effective configuration (kept with each scoring run). */
public record LayerRef(ConfigurationScope scope, String scopeId, UUID versionId, int version, String hash) {
}
