package com.davidcreate.jobhub.job.domain.service;

import com.davidcreate.jobhub.job.domain.model.JobFacets;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Short-TTL cache of the computed facets payload, keyed on the filter combo
 * only (ADR 0020 / {@link FacetCacheKey}), invalidated by a crawl-data
 * generation stamp ({@link CrawlGenerationStamp}) in addition to a per-entry
 * safety-net TTL. Bounded by {@code job.search.facets.cache.max-size} (LRU
 * eviction via access-ordered {@link LinkedHashMap}), mirrors {@link CountCache}
 * (ADR 0018).
 *
 * <p>{@link #get(FacetCacheKey, long)} only returns a value when an entry
 * exists, its {@code generation} matches the caller's current generation, and
 * it is still fresh under the TTL: a stale-generation entry never satisfies a
 * {@code get} even if it is time-fresh (AC-332-8/9/10).
 */
@ApplicationScoped
public class FacetCache {

    private final boolean enabled;
    private final Duration ttl;
    private final Map<FacetCacheKey, FacetCacheEntry> store;

    public FacetCache(@ConfigProperty(name = "job.search.facets.cache.enabled", defaultValue = "true") boolean enabled,
                       @ConfigProperty(name = "job.search.facets.cache.ttl", defaultValue = "PT60S") Duration ttl,
                       @ConfigProperty(name = "job.search.facets.cache.max-size", defaultValue = "500") int maxSize) {
        this.enabled = enabled;
        this.ttl = ttl;
        this.store = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<FacetCacheKey, FacetCacheEntry> eldest) {
                return size() > maxSize;
            }
        });
    }

    public Optional<JobFacets> get(FacetCacheKey key, long generation) {
        if (!enabled) {
            return Optional.empty();
        }
        FacetCacheEntry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.generation() != generation) {
            return Optional.empty();
        }
        if (!entry.isFresh(Instant.now(), ttl)) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public void put(FacetCacheKey key, JobFacets value, long generation) {
        if (!enabled) {
            return;
        }
        store.put(key, new FacetCacheEntry(value, Instant.now(), generation));
    }
}
