package com.company.leadershipintelligence.service.identity;

import com.company.leadershipintelligence.config.properties.RateLimitProperties;
import com.company.leadershipintelligence.domain.exception.RateLimitedException;
import com.company.leadershipintelligence.repository.security.InMemoryRateCounters;
import com.company.leadershipintelligence.support.TestClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AuthRateLimiterTest {

    private static final ClientInfo HERE = new ClientInfo("1.1.1.1", "test");
    private static final ClientInfo ELSEWHERE = new ClientInfo("2.2.2.2", "test");

    private final TestClock clock = new TestClock();
    private final SecurityAudit audit = mock(SecurityAudit.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    // 3 sign-ins per 5 minutes per address; 2 failures then a 30 s delay that doubles up to 2 minutes; 2 account calls and 2 link uses per hour
    private final RateLimitProperties limits = new RateLimitProperties("memory", 3, Duration.ofMinutes(5), 2, Duration.ofMinutes(15),
            Duration.ofSeconds(30), Duration.ofMinutes(2), 2, 2, Duration.ofHours(1), 2, Duration.ofHours(1), 3, 2, Duration.ofMinutes(1), 3, 2);
    private final AuthRateLimiter limiter = new AuthRateLimiter(new InMemoryRateCounters(clock), limits, audit, meters);

    @Test
    void anAddressIsRefusedAboveItsSignInLimitWithTheTimeToWait() {
        for (int i = 0; i < 3; i++) {
            limiter.checkLoginAddress(HERE);
        }
        assertThatThrownBy(() -> limiter.checkLoginAddress(HERE)).isInstanceOfSatisfying(RateLimitedException.class, e -> {
            assertThat(e.retryAfter()).isBetween(Duration.ofSeconds(1), Duration.ofMinutes(5));
            assertThat(e.details()).containsEntry("rule", "login-ip");
        });
        assertThatCode(() -> limiter.checkLoginAddress(ELSEWHERE)).as("another address is not affected").doesNotThrowAnyException();
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertThatCode(() -> limiter.checkLoginAddress(HERE)).doesNotThrowAnyException();
    }

    @Test
    void everyRefusalIsAuditedAndCounted() {
        for (int i = 0; i < 3; i++) {
            limiter.checkLoginAddress(HERE);
        }
        assertThatThrownBy(() -> limiter.checkLoginAddress(HERE)).isInstanceOf(RateLimitedException.class);
        assertThatThrownBy(() -> limiter.checkLoginAddress(HERE)).isInstanceOf(RateLimitedException.class);
        verify(audit, times(2)).record(eq("RATE_LIMITED"), eq(SecurityAudit.FAILURE), any(), any(), any(), eq(HERE), any());
        assertThat(meters.counter("auth.ratelimit.hits", "rule", "login-ip").count()).isEqualTo(2);
    }

    @Test
    void failuresStartADelayThatDoublesAndNeverBecomesAPermanentLock() {
        String email = "ana@example.com";
        limiter.loginFailed(email);
        assertThatCode(() -> limiter.checkLoginOpen(email, HERE)).as("below the failure limit").doesNotThrowAnyException();
        limiter.loginFailed(email);
        assertThatThrownBy(() -> limiter.checkLoginOpen(email, HERE)).isInstanceOfSatisfying(RateLimitedException.class,
                e -> assertThat(e.retryAfter()).isLessThanOrEqualTo(Duration.ofSeconds(30)));
        clock.advance(Duration.ofSeconds(31));
        assertThatCode(() -> limiter.checkLoginOpen(email, HERE)).as("the delay ended").doesNotThrowAnyException();
        limiter.loginFailed(email);
        assertThatThrownBy(() -> limiter.checkLoginOpen(email, HERE)).isInstanceOfSatisfying(RateLimitedException.class,
                e -> assertThat(e.retryAfter()).isGreaterThan(Duration.ofSeconds(30)).isLessThanOrEqualTo(Duration.ofSeconds(60)));
        for (int i = 0; i < 10; i++) {
            limiter.loginFailed(email);
        }
        assertThatThrownBy(() -> limiter.checkLoginOpen(email, HERE)).isInstanceOfSatisfying(RateLimitedException.class,
                e -> assertThat(e.retryAfter()).as("capped").isLessThanOrEqualTo(Duration.ofMinutes(2)));
        clock.advance(Duration.ofMinutes(3));
        assertThatCode(() -> limiter.checkLoginOpen(email, HERE)).as("no permanent lock").doesNotThrowAnyException();
    }

    @Test
    void theDelayBelongsToTheAccountWhateverTheAddress() {
        limiter.loginFailed("ana@example.com");
        limiter.loginFailed("ana@example.com");
        assertThatThrownBy(() -> limiter.checkLoginOpen("ana@example.com", ELSEWHERE)).isInstanceOf(RateLimitedException.class);
        assertThatCode(() -> limiter.checkLoginOpen("bob@example.com", HERE)).doesNotThrowAnyException();
    }

    @Test
    void aSuccessfulSignInClearsTheFailuresAndTheDelay() {
        limiter.loginFailed("ana@example.com");
        limiter.loginFailed("ana@example.com");
        limiter.loginSucceeded("ana@example.com");
        assertThatCode(() -> limiter.checkLoginOpen("ana@example.com", HERE)).doesNotThrowAnyException();
        limiter.loginFailed("ana@example.com");
        assertThatCode(() -> limiter.checkLoginOpen("ana@example.com", HERE)).as("the count started again").doesNotThrowAnyException();
    }

    @Test
    void accountCallsAreLimitedPerAddressAndPerEmailAndEachActionOnItsOwn() {
        limiter.checkAccountRequest("register", "ana@example.com", HERE);
        limiter.checkAccountRequest("register", "ana@example.com", ELSEWHERE);
        assertThatThrownBy(() -> limiter.checkAccountRequest("register", "ana@example.com", new ClientInfo("3.3.3.3", null)))
                .isInstanceOfSatisfying(RateLimitedException.class, e -> assertThat(e.details()).containsEntry("rule", "account-email-register"));
        assertThatCode(() -> limiter.checkAccountRequest("forgot", "ana@example.com", HERE)).as("another action has its own count").doesNotThrowAnyException();
        limiter.checkAccountRequest("resend", "x1@example.com", HERE);
        limiter.checkAccountRequest("resend", "x2@example.com", HERE);
        assertThatThrownBy(() -> limiter.checkAccountRequest("resend", "x3@example.com", HERE))
                .isInstanceOfSatisfying(RateLimitedException.class, e -> assertThat(e.details()).containsEntry("rule", "account-ip-resend"));
        assertThatCode(() -> limiter.checkAccountRequest("resend", null, ELSEWHERE)).as("an invalid email still counts for the address only").doesNotThrowAnyException();
        clock.advance(Duration.ofMinutes(61));
        assertThatCode(() -> limiter.checkAccountRequest("register", "ana@example.com", HERE)).doesNotThrowAnyException();
    }

    @Test
    void usingLinksIsLimitedPerAddress() {
        limiter.checkTokenUse(HERE);
        limiter.checkTokenUse(HERE);
        assertThatThrownBy(() -> limiter.checkTokenUse(HERE)).isInstanceOf(RateLimitedException.class);
        assertThatCode(() -> limiter.checkTokenUse(ELSEWHERE)).doesNotThrowAnyException();
    }


    @Test
    void anApiTokenIsLimitedPerTokenPerMinute() {
        java.util.UUID one = java.util.UUID.randomUUID();
        java.util.UUID two = java.util.UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            limiter.checkApiToken(one, HERE);
        }
        assertThatThrownBy(() -> limiter.checkApiToken(one, HERE)).isInstanceOfSatisfying(RateLimitedException.class,
                e -> assertThat(e.details()).containsEntry("rule", "api-token"));
        assertThatCode(() -> limiter.checkApiToken(two, HERE)).as("another token has its own count").doesNotThrowAnyException();
        clock.advance(Duration.ofSeconds(61));
        assertThatCode(() -> limiter.checkApiToken(one, HERE)).doesNotThrowAnyException();
    }

    @Test
    void anAddressThatKeepsPresentingInvalidApiTokensIsRefused() {
        limiter.apiTokenRejected(HERE);
        limiter.apiTokenRejected(HERE);
        assertThatThrownBy(() -> limiter.apiTokenRejected(HERE)).isInstanceOfSatisfying(RateLimitedException.class,
                e -> assertThat(e.details()).containsEntry("rule", "api-token-invalid"));
        assertThatCode(() -> limiter.apiTokenRejected(ELSEWHERE)).doesNotThrowAnyException();
    }
    @Test
    void theCountersHoldNoEmailAddress() {
        // the key of an email is its hash: a counter can be inspected without learning who is being throttled
        InMemoryRateCounters counters = new InMemoryRateCounters(clock) {
            @Override
            public long increment(String key, Duration window) {
                assertThat(key).doesNotContain("ana@example.com");
                return super.increment(key, window);
            }
        };
        AuthRateLimiter checking = new AuthRateLimiter(counters, limits, audit, meters);
        checking.checkAccountRequest("register", "ana@example.com", HERE);
        checking.loginFailed("ana@example.com");
    }
}
