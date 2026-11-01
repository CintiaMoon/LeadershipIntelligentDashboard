package com.company.leadershipintelligence.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Engine and algorithm versions recorded with every run (specv3.md Section 1.2). */
@ConfigurationProperties(prefix = "lis.engine")
public record EngineProperties(String version, String algorithmVersion) {
}
