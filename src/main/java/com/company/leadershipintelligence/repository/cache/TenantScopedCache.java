package com.company.leadershipintelligence.repository.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import java.time.Duration;

/**
 * Local cache whose keys always include the tenant, so one tenant's entry can never be served to another
 * (specv3.md Section 24). Entries are immutable values; invalidation is by tenant or complete. Hit and miss
 * statistics are recorded. The cache must not change a result: callers reload on invalidation or expiry.
 */
public final class TenantScopedCache<V> {

    private record Key(String tenantId, String scopeA, String scopeB) {
    }

    private final Cache<Key, V> cache;

    public TenantScopedCache(long maximumSize, Duration expireAfterWrite) {
        this.cache = Caffeine.newBuilder().maximumSize(maximumSize).expireAfterWrite(expireAfterWrite).recordStats().build();
    }

    public V getIfPresent(String tenantId, String scopeA, String scopeB) {
        return cache.getIfPresent(key(tenantId, scopeA, scopeB));
    }

    public void put(String tenantId, String scopeA, String scopeB, V value) {
        cache.put(key(tenantId, scopeA, scopeB), value);
    }

    public void invalidateTenant(String tenantId) {
        cache.asMap().keySet().removeIf(k -> k.tenantId().equals(tenantId));
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    public CacheStats stats() {
        return cache.stats();
    }

    public long size() {
        return cache.estimatedSize();
    }

    private static Key key(String tenantId, String scopeA, String scopeB) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("a cache key needs a tenant");
        }
        return new Key(tenantId, scopeA == null ? "" : scopeA, scopeB == null ? "" : scopeB);
    }
}
