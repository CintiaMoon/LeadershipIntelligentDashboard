package com.company.leadershipintelligence.repository.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Counters in Redis (decision D71), shared by every instance. Keys carry a prefix and hold no personal data. */
public class RedisRateCounters implements RateCounters {

    private static final String PREFIX = "lis:rl:";

    // INCR and the expiry of the first increment in one atomic step, so a crash cannot leave a counter that never expires
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>(
            "local n = redis.call(\"INCR\", KEYS[1]) if n == 1 then redis.call(\"PEXPIRE\", KEYS[1], ARGV[1]) end return n", Long.class);

    private final StringRedisTemplate redis;

    public RedisRateCounters(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, Duration window) {
        Long value = redis.execute(INCREMENT, List.of(PREFIX + key), Long.toString(window.toMillis()));
        if (value == null) {
            throw new IllegalStateException("Redis returned no counter value");
        }
        return value;
    }

    @Override
    public void mark(String key, Duration ttl) {
        redis.opsForValue().set(PREFIX + key, "1", ttl);
    }

    @Override
    public Optional<Duration> remaining(String key) {
        Long millis = redis.getExpire(PREFIX + key, TimeUnit.MILLISECONDS);
        // -2: no such key; -1: a key without expiry (not created by this class)
        return millis == null || millis < 0 ? Optional.empty() : Optional.of(Duration.ofMillis(millis));
    }

    @Override
    public void delete(String key) {
        redis.delete(PREFIX + key);
    }
}
