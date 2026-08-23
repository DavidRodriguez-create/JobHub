package com.davidcreate.jobhub.job.domain.service;

import com.davidcreate.jobhub.job.domain.model.JobCount;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Short-TTL cache of the computed job-search total (value + estimate flag),
 * keyed on the filter set only (ADR 0018 / {@link CountCacheKey}). Bounded by
 * {@code job.search.count.cache.max-size} (LRU eviction via access-ordered
 * {@link LinkedHashMap}). A load-shedder, not the primary mechanism, see
 * {@link CountDecision} for the estimate-above-threshold strategy this backs up.
 */
@ApplicationScoped
public class CountCache {

    private final boolean enabled;
    private final Duration ttl;
    private final Map<CountCacheKey, CountCacheEntry> store;

    public CountCache(@ConfigProperty(name = "job.search.count.cache.enabled", defaultValue = "true") boolean enabled,
                       @ConfigProperty(name = "job.search.count.cache.ttl", defaultValue = "PT30S") Duration ttl,
                       @ConfigProperty(name = "job.search.count.cache.max-size", defaultValue = "1000") int maxSize) {
        this.enabled = enabled;
        this.ttl = ttl;
        this.store = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CountCacheKey, CountCacheEntry> eldest) {
                return size() > maxSize;
            }
        });
    }

    public Optional<JobCount> get(CountCacheKey key) {
        if (!enabled) {
            return Optional.empty();
        }
        CountCacheEntry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.isFresh(Instant.now(), ttl)) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public void put(CountCacheKey key, JobCount value) {
        if (!enabled) {
            return;
        }
        store.put(key, new CountCacheEntry(value, Instant.now()));
    }
}
