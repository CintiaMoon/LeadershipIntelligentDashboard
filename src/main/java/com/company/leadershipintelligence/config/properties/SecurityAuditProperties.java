package com.company.leadershipintelligence.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * How long the security audit is kept (decision D74).
 *
 * @param retention     events older than this are deleted by the daily job; 400 days by default
 * @param purgeEnabled  false turns the daily job off (the events are then kept for ever)
 * @param purgeCron     when the job runs (Spring cron, server time); by default every day at 03:30
 */
@ConfigurationProperties(prefix = "lis.security-audit")
public record SecurityAuditProperties(
        @DefaultValue("400d") Duration retention,
        @DefaultValue("true") boolean purgeEnabled,
        @DefaultValue("0 30 3 * * *") String purgeCron) {
}
