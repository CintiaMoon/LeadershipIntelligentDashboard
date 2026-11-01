package com.company.leadershipintelligence.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock the tests can move, to check effective dates and cache staleness. */
public final class TestClock extends Clock {

    private volatile Instant now = Instant.parse("2026-11-01T10:00:00Z");

    public void set(Instant instant) {
        this.now = instant;
    }

    public void advance(java.time.Duration duration) {
        this.now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
