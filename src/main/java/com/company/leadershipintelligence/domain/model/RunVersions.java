package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;

/** Version identities that reproduce a run (specv3.md Sections 1.2, 27). Supplied by the caller. */
public record RunVersions(
        String engineVersion,
        String algorithmVersion,
        ConfigurationVersion configurationVersion,
        String roundingPolicyVersion,
        String confidenceAlgorithmVersion) {

    public RunVersions {
        if (engineVersion == null || engineVersion.isBlank() || algorithmVersion == null || algorithmVersion.isBlank()
                || configurationVersion == null || roundingPolicyVersion == null || roundingPolicyVersion.isBlank()
                || confidenceAlgorithmVersion == null || confidenceAlgorithmVersion.isBlank()) {
            throw new IllegalArgumentException("every version identity is required");
        }
    }
}
