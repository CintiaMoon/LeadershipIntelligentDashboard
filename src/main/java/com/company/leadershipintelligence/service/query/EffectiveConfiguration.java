package com.company.leadershipintelligence.service.query;

import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;

import java.util.List;

/**
 * The resolved, validated configuration for one tenant, organization and template (specv3.md Section 23.2).
 * {@code configurationHash} is the hash of the merged document; {@code layers} are the exact versions merged.
 */
public record EffectiveConfiguration(ScoringConfigurationSnapshot snapshot, String configurationHash,
                                     String effectiveVersion, List<LayerRef> layers) {

    public EffectiveConfiguration {
        layers = List.copyOf(layers);
    }
}
