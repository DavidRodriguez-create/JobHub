package com.davidcreate.jobhub.job.unit_tests.domain.service;

import com.davidcreate.jobhub.job.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.job.domain.service.CrawlGenerationStamp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The "should I re-read?" predicate is a pure function of
 * {@code (lastReadInstant, now, ttl)} (ADR 0020); the read itself is fail-soft
 * on repository errors.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CrawlGenerationStamp Unit Tests")
class CrawlGenerationStampTest {

    private static final Instant T = Instant.parse("2026-07-19T12:00:00Z");
    private static final Duration TTL = Duration.ofSeconds(10);

    @Mock
    JobPostRepository jobPostRepository;

    @Test
    @DisplayName("FC332-U-17 (AC-332-10): now = lastRead + ttl - 1ms -> re-read NOT due, facetDataVersion() not invoked again")
    void withinTtlWindowDoesNotReRead() {
        CrawlGenerationStamp stamp = new CrawlGenerationStamp(jobPostRepository, TTL);
        when(jobPostRepository.facetDataVersion()).thenReturn(100L);

        long first = stamp.current(T);
        long second = stamp.current(T.plus(TTL).minusMillis(1));

        assertThat(first).isEqualTo(100L);
        assertThat(second).isEqualTo(100L);
        verify(jobPostRepository, times(1)).facetDataVersion();
    }

    @Test
    @DisplayName("FC332-U-18 (AC-332-8/9): now = lastRead + ttl + 1ms -> re-read due, facetDataVersion() invoked again")
    void afterTtlWindowReReads() {
        CrawlGenerationStamp stamp = new CrawlGenerationStamp(jobPostRepository, TTL);
        when(jobPostRepository.facetDataVersion()).thenReturn(100L, 200L);

        long first = stamp.current(T);
        long second = stamp.current(T.plus(TTL).plusMillis(1));

        assertThat(first).isEqualTo(100L);
        assertThat(second).isEqualTo(200L);
        verify(jobPostRepository, times(2)).facetDataVersion();
    }

    @Test
    @DisplayName("FC332-U-19 (foundational): first-ever current() call invokes facetDataVersion() exactly once regardless of ttl")
    void firstEverCallAlwaysInvokes() {
        CrawlGenerationStamp stamp = new CrawlGenerationStamp(jobPostRepository, Duration.ofDays(1));
        when(jobPostRepository.facetDataVersion()).thenReturn(42L);

        long value = stamp.current(T);

        assertThat(value).isEqualTo(42L);
        verify(jobPostRepository, times(1)).facetDataVersion();
    }

    @Test
    @DisplayName("FC332-U-20 (ADR 0020 cheap version-stamp read, TTL-guarded): second call within the ttl window does not invoke facetDataVersion() again")
    void secondCallWithinTtlReusesLastKnownValue() {
        CrawlGenerationStamp stamp = new CrawlGenerationStamp(jobPostRepository, TTL);
        when(jobPostRepository.facetDataVersion()).thenReturn(7L);

        long first = stamp.current(T);
        long second = stamp.current(T.plusSeconds(1));

        assertThat(first).isEqualTo(7L);
        assertThat(second).isEqualTo(7L);
        verify(jobPostRepository, times(1)).facetDataVersion();
    }

    @Test
    @DisplayName("FC332-U-21 (ADR 0020 fail-soft): facetDataVersion() throws after a prior successful read -> returns the last-known stamp, no exception propagated")
    void throwsAfterPriorSuccessReturnsLastKnownStamp() {
        CrawlGenerationStamp stamp = new CrawlGenerationStamp(jobPostRepository, TTL);
        when(jobPostRepository.facetDataVersion())
                .thenReturn(55L)
                .thenThrow(new RuntimeException("simulated DB error"));

        long first = stamp.current(T);
        long second = stamp.current(T.plus(TTL).plusMillis(1));

        assertThat(first).isEqualTo(55L);
        assertThat(second).isEqualTo(55L);
    }

    @Test
    @DisplayName("FC332-U-22 (assumption, flagged for architect/dev confirmation): facetDataVersion() throws on the very first call -> returns 0L, never throws")
    void throwsOnVeryFirstCallReturnsSafeDefault() {
        CrawlGenerationStamp stamp = new CrawlGenerationStamp(jobPostRepository, TTL);
        when(jobPostRepository.facetDataVersion()).thenThrow(new RuntimeException("simulated cold-start DB error"));

        long value = stamp.current(T);

        assertThat(value).isEqualTo(0L);
    }
}
