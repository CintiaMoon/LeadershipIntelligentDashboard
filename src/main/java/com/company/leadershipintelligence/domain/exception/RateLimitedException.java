package com.company.leadershipintelligence.domain.exception;

import java.time.Duration;
import java.util.Map;

/** A throttle refused the call (decision D71). Carries how long to wait, which the API sends as {@code Retry-After}. */
public class RateLimitedException extends ScoringException {

    private final Duration retryAfter;

    public RateLimitedException(String rule, Duration retryAfter) {
        super(ScoringErrorCode.TOO_MANY_REQUESTS, "Too many requests. Try again later.", Map.of("rule", rule));
        this.retryAfter = retryAfter.isNegative() || retryAfter.isZero() ? Duration.ofSeconds(1) : retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
