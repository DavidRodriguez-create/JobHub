package com.davidcreate.jobhub.crawler.unit_tests.domain.service;

import com.davidcreate.jobhub.crawler.domain.exception.EnrichmentUnavailableException;
import com.davidcreate.jobhub.crawler.domain.model.EnrichBatchResult;
import com.davidcreate.jobhub.crawler.domain.model.JobEnrichment;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import com.davidcreate.jobhub.crawler.domain.port.out.JobEnricher;
import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import com.davidcreate.jobhub.crawler.domain.service.EnrichmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnrichmentService Unit Tests")
class EnrichmentServiceTest {

    @Mock JobPostRepository repository;
    @Mock JobEnricher enricher;
    @Mock TriggerRequestQueue triggerRequestQueue;

    private EnrichmentService service() {
        return new EnrichmentService(repository, enricher, triggerRequestQueue, 3);
    }

    private static JobPost job(UUID id) {
        return JobPost.builder().id(id).title("Engineer").description("desc").build();
    }

    @Test
    @DisplayName("enriches each pending job and applies the result")
    void enrichesPending() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(repository.findPendingEnrichment(5)).thenReturn(List.of(job(id1), job(id2)));
        JobEnrichment result = new JobEnrichment("full-time", "senior", null, null, null, null, null, null);
        when(enricher.enrich(any(), any(), any(), any())).thenReturn(result);

        int count = service().enrichPending(5);

        assertThat(count).isEqualTo(2);
        verify(repository).applyEnrichment(id1, result);
        verify(repository).applyEnrichment(id2, result);
        verify(repository, never()).markEnrichmentFailed(any(), anyInt());
    }

    @Test
    @DisplayName("marks a job failed when the model call throws, and keeps going")
    void marksFailedOnError() {
        UUID ok = UUID.randomUUID();
        UUID bad = UUID.randomUUID();
        when(repository.findPendingEnrichment(5)).thenReturn(List.of(job(bad), job(ok)));
        JobEnrichment result = new JobEnrichment(null, "mid", null, null, null, null, null, null);
        when(enricher.enrich(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("model down"))
                .thenReturn(result);

        int count = service().enrichPending(5);

        assertThat(count).isEqualTo(1);
        verify(repository).markEnrichmentFailed(bad, 3);
        verify(repository).applyEnrichment(ok, result);
    }

    @Test
    @DisplayName("C16: reports attempted=5, enriched=0 when every job in the batch fails")
    void reportsAttemptedAndZeroEnrichedWhenAllFail() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        UUID id4 = UUID.randomUUID();
        UUID id5 = UUID.randomUUID();
        when(repository.findPendingEnrichment(5))
                .thenReturn(List.of(job(id1), job(id2), job(id3), job(id4), job(id5)));
        when(enricher.enrich(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("model down"));

        EnrichBatchResult result = service().enrichPending(5, null);

        assertThat(result.getAttempted()).isEqualTo(5);
        assertThat(result.getEnriched()).isZero();
        assertThat(result.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("C17: reports attempted=0, enriched=0 when there are no pending rows")
    void reportsZeroAttemptedWhenNoPending() {
        when(repository.findPendingEnrichment(5)).thenReturn(List.of());

        EnrichBatchResult result = service().enrichPending(5, null);

        assertThat(result.getAttempted()).isZero();
        assertThat(result.getEnriched()).isZero();
        assertThat(result.isCancelled()).isFalse();
        verify(enricher, never()).enrich(any(), any(), any(), any());
    }

    @Test
    @DisplayName("does nothing when there are no pending jobs")
    void noPending() {
        when(repository.findPendingEnrichment(5)).thenReturn(List.of());

        int count = service().enrichPending(5);

        assertThat(count).isZero();
        verify(enricher, never()).enrich(any(), any(), any(), any());
    }

    @Test
    @DisplayName("C10: EnrichmentUnavailableException for job 2 of 5 — not marked failed, enrich invoked twice, loop breaks")
    void breaksBatchOnTransientUnavailability() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        UUID id4 = UUID.randomUUID();
        UUID id5 = UUID.randomUUID();
        when(repository.findPendingEnrichment(5))
                .thenReturn(List.of(job(id1), job(id2), job(id3), job(id4), job(id5)));
        JobEnrichment result = new JobEnrichment("full-time", "senior", null, null, null, null, null, null);
        when(enricher.enrich(any(), any(), any(), any()))
                .thenReturn(result)
                .thenThrow(new EnrichmentUnavailableException("no provider reachable"));

        service().enrichPending(5, null);

        verify(enricher, times(2)).enrich(any(), any(), any(), any());
        verify(repository).applyEnrichment(id1, result);
        verify(repository, never()).markEnrichmentFailed(eq(id2), anyInt());
        verify(repository, never()).markEnrichmentFailed(any(), anyInt());
    }

    @Test
    @DisplayName("C11: attempted/enriched reflect only the postings actually attempted before the transient break")
    void attemptedAndEnrichedReflectOnlyAttemptedPostings() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        UUID id4 = UUID.randomUUID();
        UUID id5 = UUID.randomUUID();
        when(repository.findPendingEnrichment(5))
                .thenReturn(List.of(job(id1), job(id2), job(id3), job(id4), job(id5)));
        JobEnrichment result = new JobEnrichment("full-time", "senior", null, null, null, null, null, null);
        when(enricher.enrich(any(), any(), any(), any()))
                .thenReturn(result)
                .thenThrow(new EnrichmentUnavailableException("no provider reachable"));

        EnrichBatchResult batchResult = service().enrichPending(5, null);

        assertThat(batchResult.getAttempted()).isEqualTo(2);
        assertThat(batchResult.getEnriched()).isEqualTo(1);
    }

    @Test
    @DisplayName("C12: a genuine (non-EnrichmentUnavailableException) failure still marks the job failed and continues the batch")
    void genuineFailureStillMarksFailedAndContinues() {
        UUID bad = UUID.randomUUID();
        UUID ok = UUID.randomUUID();
        when(repository.findPendingEnrichment(5)).thenReturn(List.of(job(bad), job(ok)));
        JobEnrichment result = new JobEnrichment(null, "mid", null, null, null, null, null, null);
        when(enricher.enrich(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("genuinely unusable"))
                .thenReturn(result);

        service().enrichPending(5, null);

        verify(repository).markEnrichmentFailed(bad, 3);
        verify(repository).applyEnrichment(ok, result);
        verify(enricher, times(2)).enrich(any(), any(), any(), any());
    }

    @Nested
    @DisplayName("enrichPending(int, UUID, ShutdownSignal) -- shutdown safety (story #398)")
    class ShutdownSafety {

        @Test
        @DisplayName("C2: shutdown detected after the 2nd posting stops the loop before a 3rd item starts")
        void shutdownStopsLoopAtItemBoundary() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            UUID id3 = UUID.randomUUID();
            when(repository.findPendingEnrichment(5)).thenReturn(List.of(job(id1), job(id2), job(id3)));
            JobEnrichment result = new JobEnrichment("full-time", "senior", null, null, null, null, null, null);
            when(enricher.enrich(any(), any(), any(), any())).thenReturn(result);
            ShutdownSignal shutdownSignal = mock(ShutdownSignal.class);
            when(shutdownSignal.isShuttingDown()).thenReturn(false, false, true);

            EnrichBatchResult batchResult = service().enrichPending(5, null, shutdownSignal);

            verify(enricher, times(2)).enrich(any(), any(), any(), any());
            assertThat(batchResult.getEnriched()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("enrichPending(int, UUID) — cooperative cancellation")
    class EnrichPendingWithTriggerId {

        @Test
        @DisplayName("CS-UNIT-02: exits early when cancel_requested is detected after the 2nd posting")
        void exitsEarlyOnCancelRequested() {
            UUID triggerRequestId = UUID.randomUUID();
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            UUID id3 = UUID.randomUUID();
            when(repository.findPendingEnrichment(5)).thenReturn(List.of(job(id1), job(id2), job(id3)));
            JobEnrichment result = new JobEnrichment("full-time", "senior", null, null, null, null, null, null);
            when(enricher.enrich(any(), any(), any(), any())).thenReturn(result);
            when(triggerRequestQueue.isCancelRequested(triggerRequestId))
                    .thenReturn(false, false, true);

            EnrichBatchResult batchResult = service().enrichPending(5, triggerRequestId);

            verify(enricher, times(2)).enrich(any(), any(), any(), any());
            verify(repository).applyEnrichment(id1, result);
            verify(repository).applyEnrichment(id2, result);
            verify(repository, never()).applyEnrichment(id3, result);
            assertThat(batchResult.getEnriched()).isEqualTo(2);
            assertThat(batchResult.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("processes all pending postings and reports cancelled=false when never cancelled")
        void completesNormallyWhenNotCancelled() {
            UUID triggerRequestId = UUID.randomUUID();
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            when(repository.findPendingEnrichment(5)).thenReturn(List.of(job(id1), job(id2)));
            JobEnrichment result = new JobEnrichment("full-time", "senior", null, null, null, null, null, null);
            when(enricher.enrich(any(), any(), any(), any())).thenReturn(result);
            when(triggerRequestQueue.isCancelRequested(triggerRequestId)).thenReturn(false);

            EnrichBatchResult batchResult = service().enrichPending(5, triggerRequestId);

            assertThat(batchResult.getEnriched()).isEqualTo(2);
            assertThat(batchResult.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("does not check cancellation when triggerRequestId is null (cron path)")
        void noCancellationCheckWhenTriggerIdNull() {
            UUID id1 = UUID.randomUUID();
            when(repository.findPendingEnrichment(5)).thenReturn(List.of(job(id1)));
            JobEnrichment result = new JobEnrichment("full-time", "senior", null, null, null, null, null, null);
            when(enricher.enrich(any(), any(), any(), any())).thenReturn(result);

            EnrichBatchResult batchResult = service().enrichPending(5, null);

            assertThat(batchResult.getEnriched()).isEqualTo(1);
            assertThat(batchResult.isCancelled()).isFalse();
            verify(triggerRequestQueue, never()).isCancelRequested(any());
        }
    }
}
