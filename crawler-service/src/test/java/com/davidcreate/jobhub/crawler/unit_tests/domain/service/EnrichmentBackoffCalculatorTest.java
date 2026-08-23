package com.davidcreate.jobhub.crawler.unit_tests.domain.service;

import com.davidcreate.jobhub.crawler.domain.service.EnrichmentBackoffCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story #537. Cases C1-C5. Note on C1/C2: the QAE doc's literal inputs (0 for C1,
 * 1..3 for C2) contradict C5 (consecutiveFailures=0 -> 0, "no backoff before first
 * failure") for the identical input 0. D3's own wording ("consecutive failed passes
 * -> 30, 60, 90, 120") and the EnrichmentBackoffState cases (C6-C8, which increment
 * the counter to 1/2/3/4 before calling this helper) both resolve unambiguously to
 * consecutiveFailures being 1-indexed (1st failure=1 -> 30, ..., 4th=4 -> 120). C1/C2
 * are implemented against that consistent reading (raised as a QAE case-numbering gap
 * in the handoff, not silently reinterpreted without a note).
 */
@DisplayName("EnrichmentBackoffCalculator Unit Tests")
class EnrichmentBackoffCalculatorTest {

    @Test
    @DisplayName("C1: first consecutive failure -> step-minutes delay")
    void firstFailureReturnsStepMinutes() {
        assertThat(EnrichmentBackoffCalculator.nextDelayMinutes(1, 30, 120)).isEqualTo(30);
    }

    @Test
    @DisplayName("C2: 2nd..4th consecutive failures -> additive growth (60, 90, 120)")
    void additiveGrowthForSubsequentFailures() {
        assertThat(EnrichmentBackoffCalculator.nextDelayMinutes(2, 30, 120)).isEqualTo(60);
        assertThat(EnrichmentBackoffCalculator.nextDelayMinutes(3, 30, 120)).isEqualTo(90);
        assertThat(EnrichmentBackoffCalculator.nextDelayMinutes(4, 30, 120)).isEqualTo(120);
    }

    @Test
    @DisplayName("C3: consecutiveFailures beyond the cap stays at max-minutes, never overshoots")
    void beyondCapStaysAtMax() {
        assertThat(EnrichmentBackoffCalculator.nextDelayMinutes(5, 30, 120)).isEqualTo(120);
    }

    @Test
    @DisplayName("C4: step-minutes=0 or max-minutes=0 disables the backoff (delay 0)")
    void zeroStepOrMaxDisablesBackoff() {
        assertThat(EnrichmentBackoffCalculator.nextDelayMinutes(3, 0, 120)).isZero();
        assertThat(EnrichmentBackoffCalculator.nextDelayMinutes(3, 30, 0)).isZero();
    }

    @Test
    @DisplayName("C5: no consecutive failures yet -> no backoff")
    void noFailuresYetMeansNoDelay() {
        assertThat(EnrichmentBackoffCalculator.nextDelayMinutes(0, 30, 120)).isZero();
    }
}
