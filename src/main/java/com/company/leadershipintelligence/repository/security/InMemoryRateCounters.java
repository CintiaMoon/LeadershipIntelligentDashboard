package com.company.leadershipintelligence.repository.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Counters in this process only: used when no Redis is configured, and as the fallback when Redis cannot be reached. */
public class InMemoryRateCounters implements RateCounters {

    private static final int PRUNE_ABOVE = 10_000;

    private record Entry(long count, Instant expiresAt) {
    }

    private final Clock clock;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public InMemoryRateCounters(Clock clock) {
        this.clock = clock;
    }

    @Override
    public long increment(String key, Duration window) {
        Instant now = clock.instant();
        prune(now);
        return entries.merge(key, new Entry(1, now.plus(window)),
                (old, fresh) -> now.isBefore(old.expiresAt()) ? new Entry(old.count() + 1, old.expiresAt()) : fresh).count();
    }

    @Override
    public void mark(String key, Duration ttl) {
        entries.put(key, new Entry(1, clock.instant().plus(ttl)));
    }

    @Override
    public Optional<Duration> remaining(String key) {
        Entry e = entries.get(key);
        Instant now = clock.instant();
        return e == null || !now.isBefore(e.expiresAt()) ? Optional.empty() : Optional.of(Duration.between(now, e.expiresAt()));
    }

    @Override
    public void delete(String key) {
        entries.remove(key);
    }

    private void prune(Instant now) {
        if (entries.size() > PRUNE_ABOVE) {
            entries.values().removeIf(e -> !now.isBefore(e.expiresAt()));
        }
    }
}
