package com.davidcreate.jobhub.job.unit_tests.domain.service;

import com.davidcreate.jobhub.job.domain.model.FacetValue;
import com.davidcreate.jobhub.job.domain.model.JobFacets;
import com.davidcreate.jobhub.job.domain.service.FacetCacheEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TTL-expiry check via an explicit {@code now} instant (no real sleep, no CDI
 * clock), mirrors {@code CountCacheEntryTest} (ADR 0018 / AC-331-13).
 */
@DisplayName("FacetCacheEntry Unit Tests (TTL expiry + generation accessor)")
class FacetCacheEntryTest {

    private static final Instant T = Instant.parse("2026-07-19T12:00:00Z");
    private static final Duration TTL = Duration.ofSeconds(60);

    private static JobFacets sampleFacets() {
        return new JobFacets(
                List.of(new FacetValue("Stripe", 4L)),
                List.of(new FacetValue("Spain", 5L)),
                List.of(new FacetValue("English", 6L)),
                List.of(new FacetValue("full-time", 5L)),
                List.of(new FacetValue("senior", 3L)),
                60000, 110000);
    }

    @Test
    @DisplayName("FC332-U-08 (AC-332-10): now = cachedAt + ttl - 1ms -> still fresh (reused)")
    void justBeforeTtlExpiryIsFresh() {
        FacetCacheEntry entry = new FacetCacheEntry(sampleFacets(), T, 1L);

        boolean fresh = entry.isFresh(T.plus(TTL).minusMillis(1), TTL);

        assertThat(fresh).isTrue();
    }

    @Test
    @DisplayName("FC332-U-09 (AC-332-11): now = cachedAt + ttl + 1ms -> expired (fresh compute triggered)")
    void justAfterTtlExpiryIsExpired() {
        FacetCacheEntry entry = new FacetCacheEntry(sampleFacets(), T, 1L);

        boolean fresh = entry.isFresh(T.plus(TTL).plusMillis(1), TTL);

        assertThat(fresh).isFalse();
    }

    @Test
    @DisplayName("FC332-U-10 (supports AC-332-8/9/10 mismatch check): generation() accessor returns exactly the constructed value")
    void generationAccessorReturnsConstructedValue() {
        FacetCacheEntry entry = new FacetCacheEntry(sampleFacets(), T, 42L);

        assertThat(entry.generation()).isEqualTo(42L);
    }
}
