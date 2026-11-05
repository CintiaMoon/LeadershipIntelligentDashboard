package com.company.leadershipintelligence.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings of the built-in accounts (decisions D65 to D68, D73). Every value has a safe default.
 *
 * @param passwordStrength   bcrypt cost; 12 in production, lowered only by tests
 * @param verificationTtl    how long an email verification link works
 * @param resetTtl           how long a password reset link works
 * @param sessionAbsolute    the longest a session may live, however active (the idle limit is {@code spring.session.timeout})
 * @param webBaseUrl         where the web application is served; links in emails point here
 * @param mailFrom           the sender address of every email
 * @param mailAsync          send emails on a background thread (false only in tests, so they can read the outbox at once)
 * @param auditHashKey       secret that keys the hash stored for an email that belongs to no account; empty means a random key per
 *                           process, which is fine because that hash is only for spotting repeats within one run
 * @param ipRequestsPerMinute  calls one client address may make a minute to the sign-in and account endpoints (429 above it)
 * @param loginFailureLimit    failed sign-ins for one email from one address before sign-in is refused for {@code loginLockout}
 * @param loginLockout         how long sign-in stays refused after too many failures (also the window in which failures count)
 * @param emailsPerAddressPerHour  account emails (verification, reset) sent to one address an hour; further requests are
 *                           answered as usual but send nothing, so the service cannot be used to flood a mailbox
 */
@ConfigurationProperties(prefix = "lis.identity")
public record IdentityProperties(
        @DefaultValue("12") int passwordStrength,
        @DefaultValue("24h") Duration verificationTtl,
        @DefaultValue("1h") Duration resetTtl,
        @DefaultValue("8h") Duration sessionAbsolute,
        @DefaultValue("http://localhost:3000") String webBaseUrl,
        @DefaultValue("no-reply@localhost") String mailFrom,
        @DefaultValue("true") boolean mailAsync,
        @DefaultValue("") String auditHashKey,
        @DefaultValue("60") int ipRequestsPerMinute,
        @DefaultValue("5") int loginFailureLimit,
        @DefaultValue("15m") Duration loginLockout,
        @DefaultValue("5") int emailsPerAddressPerHour) {
}
