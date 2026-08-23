package com.davidcreate.jobhub.job.unit_tests.adapter.in.scheduler;

import com.davidcreate.jobhub.job.adapter.in.scheduler.CompanyResolutionScheduler;
import com.davidcreate.jobhub.job.domain.port.in.ResolveCompaniesUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Constructed directly (mirrors crawler-service's {@code TriggerRequestScheduler}/
 * {@code EnrichmentScheduler} precedent) - no Quarkus scheduler infra needed to test the
 * gate logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyResolutionScheduler Unit Tests")
class CompanyResolutionSchedulerTest {

    @Mock
    ResolveCompaniesUseCase resolveCompaniesUseCase;

    CompanyResolutionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CompanyResolutionScheduler(resolveCompaniesUseCase);
    }

    // ── QAE-428-SCHED-01 ────────────────────────────────────────────────────────────
    // Also covers QAE-428-SVC-05: the enabled gate lives only on this scheduler adapter
    // (not on CompanyResolutionService itself), per the QAE doc's own note not to
    // duplicate the assertion in both places.

    @Test
    @DisplayName("QAE-428-SCHED-01: disabled -> the scheduled method never invokes the use case")
    void disabledNeverInvokesUseCase() {
        scheduler.enabled = false;

        scheduler.run();

        verify(resolveCompaniesUseCase, never()).resolvePending();
    }

    // ── QAE-428-SCHED-02 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-SCHED-02: enabled -> resolvePending() invoked exactly once per tick")
    void enabledInvokesResolvePendingOncePerTick() {
        scheduler.enabled = true;
        when(resolveCompaniesUseCase.resolvePending()).thenReturn(3);

        scheduler.run();

        verify(resolveCompaniesUseCase, times(1)).resolvePending();
    }
}
