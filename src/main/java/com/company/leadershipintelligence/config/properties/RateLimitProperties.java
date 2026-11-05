package com.company.leadershipintelligence.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Limits of the sign-in and account endpoints (decision D71). The values are starting points, not calibrated against real
 * traffic: tune them in configuration.
 *
 * @param store               {@code memory} (counters in this process) or {@code redis} (shared, with an in-process fallback)
 * @param loginIpLimit        sign-in calls from one address per {@code loginIpWindow}
 * @param loginFailureLimit   failed sign-ins for one account per {@code loginFailureWindow} before a delay starts
 * @param loginDelayBase      the first delay after the limit; it doubles with every further failure
 * @param loginDelayMax       the longest delay; there is no permanent lock, so the real owner is never locked out for good
 * @param accountIpLimit      register, resend-verification or forgot-password calls from one address per {@code accountWindow}, each
 * @param accountEmailLimit   the same calls for one email address per {@code accountWindow}, each
 * @param tokenIpLimit        uses of a verification or reset link from one address per {@code tokenWindow}
 * @param apiTokenLimit       calls with one API token per {@code apiTokenWindow}
 * @param apiTokenFailureLimit  calls from one address with an API token that is not valid, per {@code apiTokenWindow}
 */
@ConfigurationProperties(prefix = "lis.rate-limit")
public record RateLimitProperties(
        @DefaultValue("memory") String store,
        @DefaultValue("20") int loginIpLimit,
        @DefaultValue("5m") Duration loginIpWindow,
        @DefaultValue("5") int loginFailureLimit,
        @DefaultValue("15m") Duration loginFailureWindow,
        @DefaultValue("30s") Duration loginDelayBase,
        @DefaultValue("15m") Duration loginDelayMax,
        @DefaultValue("5") int accountIpLimit,
        @DefaultValue("5") int accountEmailLimit,
        @DefaultValue("1h") Duration accountWindow,
        @DefaultValue("20") int tokenIpLimit,
        @DefaultValue("1h") Duration tokenWindow,
        @DefaultValue("600") int apiTokenLimit,
        @DefaultValue("60") int apiTokenFailureLimit,
        @DefaultValue("1m") Duration apiTokenWindow) {
}
