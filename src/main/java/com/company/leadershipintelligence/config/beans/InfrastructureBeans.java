package com.company.leadershipintelligence.config.beans;

import com.company.leadershipintelligence.repository.cache.TenantScopedCache;
import com.company.leadershipintelligence.service.query.CachedConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class InfrastructureBeans {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Cache effectiveness as counters named like specv3.md Section 34.1 (configuration_cache_hits_total, ..._misses_total). */
    @Bean
    MeterBinder configurationCacheMeters(TenantScopedCache<CachedConfiguration> cache) {
        return registry -> {
            FunctionCounter.builder("configuration.cache.hits", cache, c -> c.stats().hitCount()).register(registry);
            FunctionCounter.builder("configuration.cache.misses", cache, c -> c.stats().missCount()).register(registry);
        };
    }

    /** Effective configurations per tenant; entries expire after five minutes at the latest and on any lifecycle change. */
    @Bean
    TenantScopedCache<CachedConfiguration> effectiveConfigurationCache() {
        return new TenantScopedCache<>(1_000, Duration.ofMinutes(5));
    }
}
