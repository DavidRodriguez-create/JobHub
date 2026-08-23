package com.davidcreate.jobhub.job.unit_tests.domain.service;

import com.davidcreate.jobhub.job.domain.model.FacetValue;
import com.davidcreate.jobhub.job.domain.model.JobFacets;
import com.davidcreate.jobhub.job.domain.service.FacetCache;
import com.davidcreate.jobhub.job.domain.service.FacetCacheKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors {@code CountCache}'s direct-construction test pattern. Eviction,
 * enabled/disabled, and generation-mismatch behaviour don't depend on elapsed
 * time, so this executes instantly against a real {@code Instant.now()}, no
 * fake clock needed (ADR 0020).
 */
@DisplayName("FacetCache Unit Tests")
class FacetCacheTest {

    private static FacetCacheKey key(String keyword) {
        return new FacetCacheKey(keyword, null, null, null, null, null, null, null, null);
    }

    private static JobFacets facets(String company) {
        return new JobFacets(
                List.of(new FacetValue(company, 1L)),
                List.of(), List.of(), List.of(), List.of(),
                null, null);
    }

    @Test
    @DisplayName("FC332-U-11 (AC-332-6): put(key, value, gen) then immediately get(key, gen) -> present, same value")
    void putThenGetSameGenerationHits() {
        FacetCache cache = new FacetCache(true, Duration.ofSeconds(60), 500);
        FacetCacheKey key = key("java");
        JobFacets value = facets("Stripe");

        cache.put(key, value, 1L);
        Optional<JobFacets> result = cache.get(key, 1L);

        assertThat(result).contains(value);
    }

    @Test
    @DisplayName("FC332-U-12 (AC-332-8/9/10): put(key, value, gen) then get(key, gen + 1) -> miss (generation mismatch, still time-fresh)")
    void generationMismatchIsAMissEvenWhenTimeFresh() {
        FacetCache cache = new FacetCache(true, Duration.ofSeconds(60), 500);
        FacetCacheKey key = key("java");
        JobFacets value = facets("Stripe");

        cache.put(key, value, 1L);
        Optional<JobFacets> result = cache.get(key, 2L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("FC332-U-13 (foundational): get(key, gen) for a key never put -> baseline miss")
    void neverPutKeyIsAMiss() {
        FacetCache cache = new FacetCache(true, Duration.ofSeconds(60), 500);

        Optional<JobFacets> result = cache.get(key("nothing"), 1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("FC332-U-14 (AC-332-17/18): enabled=false -> always a miss, put is a no-op")
    void disabledCacheAlwaysMissesAndPutIsANoOp() {
        FacetCache cache = new FacetCache(false, Duration.ofSeconds(60), 500);
        FacetCacheKey key = key("java");
        JobFacets value = facets("Stripe");

        cache.put(key, value, 1L);
        Optional<JobFacets> result = cache.get(key, 1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("FC332-U-15 (ADR 0020 bounded LRU): max-size=2, put A/B/C with no intervening get -> A (least-recently-used) evicted")
    void exceedingMaxSizeEvictsLeastRecentlyUsed() {
        FacetCache cache = new FacetCache(true, Duration.ofSeconds(60), 2);
        FacetCacheKey a = key("A");
        FacetCacheKey b = key("B");
        FacetCacheKey c = key("C");

        cache.put(a, facets("A"), 1L);
        cache.put(b, facets("B"), 1L);
        cache.put(c, facets("C"), 1L);

        assertThat(cache.get(a, 1L)).isEmpty();
        assertThat(cache.get(b, 1L)).isPresent();
        assertThat(cache.get(c, 1L)).isPresent();
    }

    @Test
    @DisplayName("FC332-U-16 (ADR 0020 access-ordered LRU): put A/B, get A (touches it), put C -> B evicted, A/C present")
    void accessOrderProtectsRecentlyTouchedEntry() {
        FacetCache cache = new FacetCache(true, Duration.ofSeconds(60), 2);
        FacetCacheKey a = key("A");
        FacetCacheKey b = key("B");
        FacetCacheKey c = key("C");

        cache.put(a, facets("A"), 1L);
        cache.put(b, facets("B"), 1L);
        cache.get(a, 1L);
        cache.put(c, facets("C"), 1L);

        assertThat(cache.get(b, 1L)).isEmpty();
        assertThat(cache.get(a, 1L)).isPresent();
        assertThat(cache.get(c, 1L)).isPresent();
    }
}
