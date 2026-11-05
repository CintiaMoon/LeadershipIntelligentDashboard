package com.company.leadershipintelligence.repository.security;

import com.company.leadershipintelligence.support.TestClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The Redis counters against a real Redis, and the fallback to counters in this process when Redis cannot be reached. */
class RedisRateCountersTest {

    private static GenericContainer<?> redis;
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate template;

    @BeforeAll
    static void start() {
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        redis.start();
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
    }

    @AfterAll
    static void stop() {
        factory.destroy();
        redis.stop();
    }

    private static String key() {
        return "test|" + UUID.randomUUID();
    }

    @Test
    void aCounterCountsAndExpiresAfterItsWindow() throws Exception {
        RedisRateCounters counters = new RedisRateCounters(template);
        String key = key();
        assertThat(counters.increment(key, Duration.ofMillis(700))).isEqualTo(1);
        assertThat(counters.increment(key, Duration.ofMillis(700))).isEqualTo(2);
        assertThat(counters.remaining(key)).hasValueSatisfying(left -> assertThat(left).isLessThanOrEqualTo(Duration.ofMillis(700)));
        Thread.sleep(900);
        assertThat(counters.remaining(key)).isEmpty();
        assertThat(counters.increment(key, Duration.ofSeconds(30))).as("a new window starts at 1").isEqualTo(1);
    }

    @Test
    void theWindowStartsAtTheFirstIncrementAndIsNotExtendedByTheNext() {
        RedisRateCounters counters = new RedisRateCounters(template);
        String key = key();
        counters.increment(key, Duration.ofSeconds(60));
        Duration first = counters.remaining(key).orElseThrow();
        counters.increment(key, Duration.ofSeconds(3600));
        assertThat(counters.remaining(key).orElseThrow()).isLessThanOrEqualTo(first);
    }

    @Test
    void aMarkIsVisibleUntilItsTimeIsUpAndCanBeDeleted() {
        RedisRateCounters counters = new RedisRateCounters(template);
        String key = key();
        assertThat(counters.remaining(key)).isEmpty();
        counters.mark(key, Duration.ofSeconds(30));
        assertThat(counters.remaining(key)).isPresent();
        counters.delete(key);
        assertThat(counters.remaining(key)).isEmpty();
    }

    @Test
    void twoInstancesShareTheSameCounter() {
        RedisRateCounters one = new RedisRateCounters(template);
        RedisRateCounters other = new RedisRateCounters(template);
        String key = key();
        one.increment(key, Duration.ofSeconds(30));
        assertThat(other.increment(key, Duration.ofSeconds(30))).isEqualTo(2);
    }

    @Test
    void whenRedisIsDownTheCountersKeepWorkingInThisProcessAndTheFallbackIsCounted() {
        LettuceConnectionFactory dead = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
        dead.setValidateConnection(false);
        dead.afterPropertiesSet();
        StringRedisTemplate deadTemplate = new StringRedisTemplate(dead);
        deadTemplate.afterPropertiesSet();
        TestClock clock = new TestClock();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        ResilientRateCounters counters = new ResilientRateCounters(new RedisRateCounters(deadTemplate), new InMemoryRateCounters(clock), meters, clock);
        try {
            String key = key();
            assertThat(counters.increment(key, Duration.ofMinutes(1))).isEqualTo(1);
            assertThat(counters.increment(key, Duration.ofMinutes(1))).as("still counting, so the limit still applies").isEqualTo(2);
            counters.mark(key + "m", Duration.ofSeconds(30));
            assertThat(counters.remaining(key + "m")).isPresent();
            counters.delete(key + "m");
            assertThat(counters.remaining(key + "m")).isEmpty();
            assertThat(meters.counter("auth.ratelimit.fallback").count()).isGreaterThanOrEqualTo(5);
            // Redis is tried again after the pause
            clock.advance(Duration.ofSeconds(11));
            assertThat(counters.increment(key, Duration.ofMinutes(1))).isEqualTo(3);
        } finally {
            dead.destroy();
        }
    }
}
