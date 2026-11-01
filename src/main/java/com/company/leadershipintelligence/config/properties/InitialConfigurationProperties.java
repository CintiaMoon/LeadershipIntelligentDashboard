package com.company.leadershipintelligence.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the platform configuration comes from in this version: a JSON file read when the application starts
 * ({@code lis.configuration.initial.*}).
 *
 * @param enabled  read the file at startup
 * @param location a Spring resource: {@code classpath:...} for the bundled initial configuration, or {@code file:/path/platform.json}
 *                 for a configuration kept outside the application
 * @param mode     {@code IF_MISSING} activates the file only when no platform configuration is active and otherwise leaves the
 *                 active one alone (and says so when it differs); {@code SYNC} also activates a changed file as a new version,
 *                 which archives the previous one. An active version is never edited in place.
 */
@ConfigurationProperties(prefix = "lis.configuration.initial")
public record InitialConfigurationProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("classpath:config/initial-platform-default.json") String location,
        @DefaultValue("IF_MISSING") Mode mode) {

    public enum Mode { IF_MISSING, SYNC }
}
