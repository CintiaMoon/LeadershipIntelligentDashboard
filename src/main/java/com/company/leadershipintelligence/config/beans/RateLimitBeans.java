package com.company.leadershipintelligence.config.beans;

import com.company.leadershipintelligence.config.properties.RateLimitProperties;
import com.company.leadershipintelligence.repository.security.InMemoryRateCounters;
import com.company.leadershipintelligence.repository.security.RateCounters;
import com.company.leadershipintelligence.repository.security.RedisRateCounters;
import com.company.leadershipintelligence.repository.security.ResilientRateCounters;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

/** Where the throttle counters live (decision D71): in this process, or in Redis with an in-process fallback. */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitBeans {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitBeans.class);

    @Bean
    RateCounters rateCounters(RateLimitProperties properties, ObjectProvider<StringRedisTemplate> redis, MeterRegistry meters, Clock clock) {
        InMemoryRateCounters local = new InMemoryRateCounters(clock);
        if (!"redis".equalsIgnoreCase(properties.store())) {
            LOG.warn("rate limit counters are kept in this process only (lis.rate-limit.store=memory); set it to redis when more than one instance runs");
            return local;
        }
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("lis.rate-limit.store=redis needs a Redis connection (set spring.data.redis.host)");
        }
        return new ResilientRateCounters(new RedisRateCounters(template), local, meters, clock);
    }
}
