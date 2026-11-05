package com.company.leadershipintelligence.service.identity;

import com.company.leadershipintelligence.config.properties.IdentityProperties;
import com.company.leadershipintelligence.support.TestClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimiterTest {

    private final TestClock clock = new TestClock();
    // 3 calls a minute, 2 failed sign-ins lock for 15 minutes, 2 emails an hour
    private final IdentityProperties properties = new IdentityProperties(4, Duration.ofHours(24), Duration.ofHours(1), Duration.ofHours(8),
            "http://localhost", "no-reply@localhost", false, "", 3, 2, Duration.ofMinutes(15), 2);
    private final AuthRateLimiter limiter = new AuthRateLimiter(properties, clock);

    @Test
    void anAddressIsRefusedAboveItsPerMinuteLimitAndIsAllowedAgainInTheNextMinute() {
        assertThat(limiter.allowRequest("1.1.1.1")).isTrue();
        assertThat(limiter.allowRequest("1.1.1.1")).isTrue();
        assertThat(limiter.allowRequest("1.1.1.1")).isTrue();
        assertThat(limiter.allowRequest("1.1.1.1")).isFalse();
        assertThat(limiter.allowRequest("2.2.2.2")).as("another address is not affected").isTrue();
        clock.advance(Duration.ofSeconds(61));
        assertThat(limiter.allowRequest("1.1.1.1")).isTrue();
    }

    @Test
    void signInClosesAfterTheFailureLimitForThatEmailAndAddressOnly() {
        assertThat(limiter.loginOpen("a@example.com", "1.1.1.1")).isTrue();
        limiter.loginFailed("a@example.com", "1.1.1.1");
        assertThat(limiter.loginOpen("a@example.com", "1.1.1.1")).isTrue();
        limiter.loginFailed("a@example.com", "1.1.1.1");
        assertThat(limiter.loginOpen("a@example.com", "1.1.1.1")).isFalse();
        assertThat(limiter.loginOpen("a@example.com", "9.9.9.9")).as("a stranger cannot lock the owner out from elsewhere").isTrue();
        assertThat(limiter.loginOpen("b@example.com", "1.1.1.1")).isTrue();
        clock.advance(Duration.ofMinutes(16));
        assertThat(limiter.loginOpen("a@example.com", "1.1.1.1")).isTrue();
    }

    @Test
    void aSuccessfulSignInClearsTheFailures() {
        limiter.loginFailed("a@example.com", "1.1.1.1");
        limiter.loginSucceeded("a@example.com", "1.1.1.1");
        limiter.loginFailed("a@example.com", "1.1.1.1");
        assertThat(limiter.loginOpen("a@example.com", "1.1.1.1")).isTrue();
    }

    @Test
    void anAddressGetsALimitedNumberOfEmailsAnHour() {
        assertThat(limiter.allowMail("a@example.com")).isTrue();
        assertThat(limiter.allowMail("a@example.com")).isTrue();
        assertThat(limiter.allowMail("a@example.com")).isFalse();
        assertThat(limiter.allowMail("b@example.com")).isTrue();
        clock.advance(Duration.ofMinutes(61));
        assertThat(limiter.allowMail("a@example.com")).isTrue();
    }
}
