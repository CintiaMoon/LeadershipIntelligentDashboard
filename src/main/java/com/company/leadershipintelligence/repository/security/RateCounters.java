package com.company.leadershipintelligence.repository.security;

import java.time.Duration;
import java.util.Optional;

/**
 * Counters with an expiry, for the throttles of the sign-in and account endpoints (decision D71). Counters only: nothing here
 * is a source of truth, and a lost counter only makes a throttle more lenient for one window.
 */
public interface RateCounters {

    /** Adds one to the counter and returns the new value; the first increment starts a window of this length. */
    long increment(String key, Duration window);

    /** Creates a marker that disappears after {@code ttl}, replacing any earlier one. */
    void mark(String key, Duration ttl);

    /** Time left on a marker or counter, or empty when there is none. */
    Optional<Duration> remaining(String key);

    void delete(String key);
}
