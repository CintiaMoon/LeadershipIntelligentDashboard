package com.company.leadershipintelligence.repository.security;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Shared counters with a safety net (decision D71, question 6): when Redis cannot be reached the throttles do not switch off.
 * They use counters in this process instead, a metric ({@code auth.ratelimit.fallback}) is counted and a warning is logged, and
 * Redis is tried again after a short pause so a dead server does not slow every request.
 */
public class ResilientRateCounters implements RateCounters {

    private static final Logger LOG = LoggerFactory.getLogger(ResilientRateCounters.class);
    private static final Duration PAUSE = Duration.ofSeconds(10);

    private final RateCounters primary;
    private final RateCounters fallback;
    private final MeterRegistry meters;
    private final Clock clock;
    private final AtomicReference<Instant> skipPrimaryUntil = new AtomicReference<>(Instant.MIN);

    public ResilientRateCounters(RateCounters primary, RateCounters fallback, MeterRegistry meters, Clock clock) {
        this.primary = primary;
        this.fallback = fallback;
        this.meters = meters;
        this.clock = clock;
    }

    @Override
    public long increment(String key, Duration window) {
        return call(c -> c.increment(key, window));
    }

    @Override
    public void mark(String key, Duration ttl) {
        call(c -> {
            c.mark(key, ttl);
            return null;
        });
    }

    @Override
    public Optional<Duration> remaining(String key) {
        return call(c -> c.remaining(key));
    }

    @Override
    public void delete(String key) {
        call(c -> {
            c.delete(key);
            return null;
        });
    }

    private <T> T call(Function<RateCounters, T> action) {
        Instant now = clock.instant();
        if (now.isBefore(skipPrimaryUntil.get())) {
            meters.counter("auth.ratelimit.fallback").increment();
            return action.apply(fallback);
        }
        try {
            return action.apply(primary);
        } catch (RuntimeException e) {
            if (skipPrimaryUntil.getAndSet(now.plus(PAUSE)).isBefore(now)) {
                LOG.warn("the shared rate limit counters are unreachable; using counters in this process type={}", e.getClass().getSimpleName());
            }
            meters.counter("auth.ratelimit.fallback").increment();
            return action.apply(fallback);
        }
    }
}
