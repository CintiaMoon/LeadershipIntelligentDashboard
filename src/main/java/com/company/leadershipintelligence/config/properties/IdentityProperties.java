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
 * @param invitationTtl      how long an invitation to join a company works
 * @param bootstrapAdminEmail  when no platform administrator exists at startup, the account with this email becomes one (it is
 *                           created if needed and sent a link to set its password); empty means none is created
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
        @DefaultValue("7d") Duration invitationTtl,
        @DefaultValue("") String bootstrapAdminEmail) {
}
