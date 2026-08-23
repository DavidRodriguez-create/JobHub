package com.davidcreate.jobhub.job.unit_tests.domain.service;

import com.davidcreate.jobhub.job.domain.model.JobCount;
import com.davidcreate.jobhub.job.domain.service.CountCacheEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TTL-expiry check via an explicit {@code now} instant (no real sleep, no CDI
 * clock), ADR 0018 / AC-331-13. See {@code CountCache} for the component that
 * wires this against the actual current time in production.
 */
@DisplayName("CountCacheEntry Unit Tests (TTL expiry)")
class CountCacheEntryTest {

    private static final Instant T = Instant.parse("2026-07-19T12:00:00Z");
    private static final Duration TTL = Duration.ofSeconds(30);

    @Test
    @DisplayName("TC-331-15 (AC-331-13): now = T + ttl - 1ms -> still fresh (reused)")
    void justBeforeTtlExpiryIsFresh() {
        CountCacheEntry entry = new CountCacheEntry(new JobCount(11, false), T);

        boolean fresh = entry.isFresh(T.plus(TTL).minusMillis(1), TTL);

        assertThat(fresh).isTrue();
    }

    @Test
    @DisplayName("TC-331-16 (AC-331-13): now = T + ttl + 1ms -> expired (fresh compute triggered)")
    void justAfterTtlExpiryIsExpired() {
        CountCacheEntry entry = new CountCacheEntry(new JobCount(11, false), T);

        boolean fresh = entry.isFresh(T.plus(TTL).plusMillis(1), TTL);

        assertThat(fresh).isFalse();
    }
}
