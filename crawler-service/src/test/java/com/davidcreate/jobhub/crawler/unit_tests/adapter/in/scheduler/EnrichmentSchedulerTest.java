package com.davidcreate.jobhub.crawler.unit_tests.adapter.in.scheduler;

import com.davidcreate.jobhub.crawler.adapter.in.scheduler.EnrichmentBackoffState;
import com.davidcreate.jobhub.crawler.adapter.in.scheduler.EnrichmentScheduler;
import com.davidcreate.jobhub.crawler.domain.model.EnrichBatchResult;
import com.davidcreate.jobhub.crawler.domain.port.in.EnrichJobsUseCase;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnrichmentScheduler Unit Tests")
class EnrichmentSchedulerTest {

    @Mock
    EnrichJobsUseCase enrichJobsUseCase;

    @Mock
    EnrichmentBackoffState backoffState;

    @Mock
    ShutdownSignal shutdownSignal;

    EnrichmentScheduler scheduler;

    private static final int BATCH_SIZE = 5;

    @BeforeEach
    void setUp() {
        scheduler = new EnrichmentScheduler(enrichJobsUseCase, backoffState, shutdownSignal);
        scheduler.enabled = true;
        scheduler.batchSize = BATCH_SIZE;
    }

    @Test
    @DisplayName("C13: skips the tick without invoking enrichPending when the pass is backed off")
    void skipsTickWhenBackedOff() {
        when(backoffState.isBackedOff(any())).thenReturn(true);

        scheduler.run();

        verify(enrichJobsUseCase, never()).enrichPending(anyInt(), any(), any());
        verify(backoffState, never()).onPassResult(anyInt(), anyInt(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    @DisplayName("C14: invokes enrichPending once and reports the outcome back to the backoff holder")
    void invokesEnrichPendingAndReportsOutcome() {
        when(backoffState.isBackedOff(any())).thenReturn(false);
        when(enrichJobsUseCase.enrichPending(eq(BATCH_SIZE), isNull(), eq(shutdownSignal)))
                .thenReturn(EnrichBatchResult.builder().attempted(5).enriched(0).cancelled(false).build());

        scheduler.run();

        verify(enrichJobsUseCase, times(1)).enrichPending(BATCH_SIZE, null, shutdownSignal);
        verify(backoffState, times(1)).onPassResult(eq(5), eq(0), eq(false), any(Instant.class));
    }

    @Test
    @DisplayName("C15: does nothing (no enrichPending, no backoff check/update) when enrichment is disabled")
    void doesNothingWhenDisabled() {
        scheduler.enabled = false;

        scheduler.run();

        verify(enrichJobsUseCase, never()).enrichPending(anyInt(), any(), any());
        verify(backoffState, never()).isBackedOff(any());
        verify(backoffState, never()).onPassResult(anyInt(), anyInt(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    @DisplayName("C3: shutdown in progress -- run() returns at once with zero repository calls")
    void shutdownInProgressReturnsAtOnceWithZeroRepoCalls() {
        when(shutdownSignal.isShuttingDown()).thenReturn(true);

        scheduler.run();

        verify(enrichJobsUseCase, never()).enrichPending(anyInt(), any(), any());
        verify(backoffState, never()).isBackedOff(any());
        verify(backoffState, never()).onPassResult(anyInt(), anyInt(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }
}
