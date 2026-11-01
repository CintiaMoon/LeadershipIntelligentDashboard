package com.company.leadershipintelligence.domain.valueobject;

/** Identity of a scoring configuration version. */
public record ConfigurationVersion(String value) {

    public ConfigurationVersion {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ConfigurationVersion must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
