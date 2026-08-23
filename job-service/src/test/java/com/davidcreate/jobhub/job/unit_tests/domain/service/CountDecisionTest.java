package com.davidcreate.jobhub.job.unit_tests.domain.service;

import com.davidcreate.jobhub.job.domain.model.CountMode;
import com.davidcreate.jobhub.job.domain.service.CountDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure {@code (plannerEstimate, mode, threshold) -> EXACT | ESTIMATE} decision.
 * No DB, no repository, no CDI. See ADR 0018 / story #331 (sub-issue #380).
 */
@DisplayName("CountDecision Unit Tests")
class CountDecisionTest {

    @Test
    @DisplayName("TC-331-1 (AC-331-8): mode=exact stays EXACT regardless of threshold, even far above it")
    void modeExactOverridesThreshold() {
        boolean isEstimate = CountDecision.isEstimate(1_000_000L, CountMode.EXACT, 10L);

        assertThat(isEstimate).isFalse();
    }

    @Test
    @DisplayName("TC-331-2 (AC-331-9): mode=estimate stays ESTIMATE regardless of threshold, even far below it")
    void modeEstimateOverridesThreshold() {
        boolean isEstimate = CountDecision.isEstimate(1L, CountMode.ESTIMATE, 1_000_000L);

        assertThat(isEstimate).isTrue();
    }

    @Test
    @DisplayName("TC-331-3 (AC-331-3): mode=hybrid, estimate exactly at threshold -> EXACT (inclusive boundary)")
    void hybridAtThresholdIsExact() {
        boolean isEstimate = CountDecision.isEstimate(1000L, CountMode.HYBRID, 1000L);

        assertThat(isEstimate).isFalse();
    }

    @Test
    @DisplayName("TC-331-4 (AC-331-4): mode=hybrid, estimate one above threshold -> ESTIMATE")
    void hybridOneAboveThresholdIsEstimate() {
        boolean isEstimate = CountDecision.isEstimate(1001L, CountMode.HYBRID, 1000L);

        assertThat(isEstimate).isTrue();
    }

    @Test
    @DisplayName("TC-331-5 (AC-331-1): mode=hybrid, estimate well below threshold -> EXACT")
    void hybridWellBelowThresholdIsExact() {
        boolean isEstimate = CountDecision.isEstimate(5L, CountMode.HYBRID, 1000L);

        assertThat(isEstimate).isFalse();
    }

    @Test
    @DisplayName("TC-331-6 (AC-331-4/7): mode=hybrid, estimate well above threshold -> ESTIMATE")
    void hybridWellAboveThresholdIsEstimate() {
        boolean isEstimate = CountDecision.isEstimate(50_000L, CountMode.HYBRID, 1000L);

        assertThat(isEstimate).isTrue();
    }

    @Test
    @DisplayName("TC-331-7 (AC-331-22): mode=hybrid, estimate of zero -> EXACT (zero is never flagged as an estimate)")
    void hybridZeroEstimateIsExact() {
        boolean isEstimate = CountDecision.isEstimate(0L, CountMode.HYBRID, 1000L);

        assertThat(isEstimate).isFalse();
    }
}
