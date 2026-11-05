package com.company.leadershipintelligence.service.identity;

import com.company.leadershipintelligence.config.properties.RateLimitProperties;
import com.company.leadershipintelligence.domain.exception.RateLimitedException;
import com.company.leadershipintelligence.repository.security.RateCounters;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * The throttles of the sign-in and account endpoints (decision D71). Every rule counts what the caller sent, never whether an
 * account exists, so a refusal reveals nothing about who has one. A refused call is audited ({@code RATE_LIMITED}), counted
 * ({@code auth.ratelimit.hits}) and answered 429 with {@code Retry-After}.
 *
 * <ul>
 *   <li>sign-in: per address {@code loginIpLimit} per {@code loginIpWindow}; per account (email hash) a failure counter, and from
 *       {@code loginFailureLimit} failures a delay that doubles with every further failure up to {@code loginDelayMax}, never a
 *       permanent lock;</li>
 *   <li>register, resend verification, forgot password: per address and per email, {@code accountIpLimit} /
 *       {@code accountEmailLimit} per {@code accountWindow}, counted for each of the three separately;</li>
 *   <li>using a verification or reset link: per address {@code tokenIpLimit} per {@code tokenWindow}.</li>
 * </ul>
 */
@Component
public class AuthRateLimiter {

    private final RateCounters counters;
    private final RateLimitProperties limits;
    private final SecurityAudit audit;
    private final MeterRegistry meters;

    public AuthRateLimiter(RateCounters counters, RateLimitProperties limits, SecurityAudit audit, MeterRegistry meters) {
        this.counters = counters;
        this.limits = limits;
        this.audit = audit;
        this.meters = meters;
    }

    /** A sign-in call from this address. */
    public void checkLoginAddress(ClientInfo client) {
        hit("login-ip", "login-ip|" + ip(client), limits.loginIpLimit(), limits.loginIpWindow(), client);
    }

    /** Refuses while the account (identified by the normalized email) is in its delay after repeated failures. */
    public void checkLoginOpen(String email, ClientInfo client) {
        counters.remaining("login-delay|" + hash(email)).ifPresent(left -> refuse("login-account", left, client));
    }

    public void loginFailed(String email) {
        String key = hash(email);
        long failures = counters.increment("login-fail|" + key, limits.loginFailureWindow());
        if (failures >= limits.loginFailureLimit()) {
            long doublings = Math.min(failures - limits.loginFailureLimit(), 20);
            Duration delay = limits.loginDelayBase().multipliedBy(1L << doublings);
            counters.mark("login-delay|" + key, delay.compareTo(limits.loginDelayMax()) > 0 ? limits.loginDelayMax() : delay);
        }
    }

    public void loginSucceeded(String email) {
        String key = hash(email);
        counters.delete("login-fail|" + key);
        counters.delete("login-delay|" + key);
    }

    /**
     * One call to register, resend-verification or forgot-password ({@code action}). The email may be null for a request whose
     * address is not valid; only the address rule applies then.
     */
    public void checkAccountRequest(String action, String email, ClientInfo client) {
        hit("account-ip-" + action, action + "-ip|" + ip(client), limits.accountIpLimit(), limits.accountWindow(), client);
        if (email != null) {
            hit("account-email-" + action, action + "-email|" + hash(email), limits.accountEmailLimit(), limits.accountWindow(), client);
        }
    }

    /** One call with an API token (decision D70, D71: 600 a minute per token). */
    public void checkApiToken(java.util.UUID tokenId, ClientInfo client) {
        hit("api-token", "api-token|" + tokenId, limits.apiTokenLimit(), limits.apiTokenWindow(), client);
    }

    /** One call from this address with an API token that is not valid; an address that keeps trying is refused. */
    public void apiTokenRejected(ClientInfo client) {
        hit("api-token-invalid", "api-token-invalid|" + ip(client), limits.apiTokenFailureLimit(), limits.apiTokenWindow(), client);
    }

    /** One use of a verification or reset link. */
    public void checkTokenUse(ClientInfo client) {
        hit("token-ip", "token-ip|" + ip(client), limits.tokenIpLimit(), limits.tokenWindow(), client);
    }

    private void hit(String rule, String key, int limit, Duration window, ClientInfo client) {
        long count = counters.increment(key, window);
        if (count > limit) {
            refuse(rule, counters.remaining(key).orElse(window), client);
        }
    }

    private void refuse(String rule, Duration retryAfter, ClientInfo client) {
        meters.counter("auth.ratelimit.hits", "rule", rule).increment();
        audit.record("RATE_LIMITED", SecurityAudit.FAILURE, null, null, null, client, Map.of("rule", rule));
        throw new RateLimitedException(rule, retryAfter);
    }

    private static String ip(ClientInfo client) {
        return client == null || client.ip() == null ? "unknown" : client.ip();
    }

    /** SHA-256 of the normalized email: the same on every instance, and no address is stored in the counters. */
    private static String hash(String email) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
