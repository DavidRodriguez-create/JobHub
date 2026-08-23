package com.davidcreate.jobhub.crawler.unit_tests.adapter.in.scheduler;

import com.davidcreate.jobhub.crawler.adapter.in.scheduler.EnrichmentBackoffState;
import com.davidcreate.jobhub.crawler.adapter.in.scheduler.TriggerRequestScheduler;
import com.davidcreate.jobhub.crawler.domain.model.CrawlBatchResult;
import com.davidcreate.jobhub.crawler.domain.model.EnrichBatchResult;
import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.model.TriggerRequest;
import com.davidcreate.jobhub.crawler.domain.model.TriggerStatus;
import com.davidcreate.jobhub.crawler.domain.port.in.CrawlUseCase;
import com.davidcreate.jobhub.crawler.domain.port.in.EnrichJobsUseCase;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownFlag;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TriggerRequestScheduler Unit Tests")
class TriggerRequestSchedulerTest {

    @Mock
    TriggerRequestQueue triggerRequestQueue;

    @Mock
    CrawlUseCase crawlUseCase;

    @Mock
    EnrichJobsUseCase enrichJobsUseCase;

    @Mock
    EnrichmentBackoffState backoffState;

    @Mock
    ShutdownSignal shutdownSignal;

    TriggerRequestScheduler scheduler;

    private static final int MIN_NEW_POSTS = 100;
    private static final int ENRICH_BATCH_SIZE = 5;

    @BeforeEach
    void setUp() {
        scheduler = new TriggerRequestScheduler(triggerRequestQueue, crawlUseCase, enrichJobsUseCase, backoffState,
                shutdownSignal);
        scheduler.minNewPosts = MIN_NEW_POSTS;
        scheduler.enrichBatchSize = ENRICH_BATCH_SIZE;
    }

    @AfterEach
    void resetShutdownFlag() throws Exception {
        // ShutdownFlag is a plain static (deliberately, so it stays readable without CDI --
        // ADR 0032, story #398, D1, 4th pass); reset it via reflection so a test that raises
        // it never leaks into a sibling test class sharing this JVM fork. No production reset
        // method exists on purpose: real shutdown is never un-raised.
        Field flagField = ShutdownFlag.class.getDeclaredField("shuttingDown");
        flagField.setAccessible(true);
        flagField.set(null, false);
    }

    private TriggerRequest queuedRequest(UUID id, TriggerKind kind) {
        return TriggerRequest.builder()
                .id(id)
                .kind(kind)
                .status(TriggerStatus.QUEUED)
                .requestedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("CR-U-01: delegates to CrawlUseCase when a crawl row is claimed")
    void delegatesToCrawlUseCaseWhenCrawlRowClaimed() {
        UUID id = UUID.randomUUID();
        when(triggerRequestQueue.claimNext(TriggerKind.CRAWL)).thenReturn(Optional.of(queuedRequest(id, TriggerKind.CRAWL)));
        when(triggerRequestQueue.claimNext(TriggerKind.ENRICHMENT)).thenReturn(Optional.empty());
        when(crawlUseCase.crawlBatch(MIN_NEW_POSTS, id, shutdownSignal)).thenReturn(
                CrawlBatchResult.builder().crawled(5).newPosts(80).hasMore(false).cancelled(false).build());

        scheduler.run();

        verify(triggerRequestQueue, times(1)).markRunning(id);
        verify(crawlUseCase, times(1)).crawlBatch(MIN_NEW_POSTS, id, shutdownSignal);
        verify(triggerRequestQueue, times(1))
                .markDone(eq(id), eq("succeeded"), any(), any(String.class), isNull());
        verify(enrichJobsUseCase, never()).enrichPending(anyInt(), any(), any());
    }

    @Test
    @DisplayName("CR-U-02: delegates to EnrichJobsUseCase when an enrichment row is claimed")
    void delegatesToEnrichJobsUseCaseWhenEnrichmentRowClaimed() {
        UUID id = UUID.randomUUID();
        when(triggerRequestQueue.claimNext(TriggerKind.CRAWL)).thenReturn(Optional.empty());
        when(triggerRequestQueue.claimNext(TriggerKind.ENRICHMENT)).thenReturn(Optional.of(queuedRequest(id, TriggerKind.ENRICHMENT)));
        when(enrichJobsUseCase.enrichPending(ENRICH_BATCH_SIZE, id, shutdownSignal)).thenReturn(
                EnrichBatchResult.builder().enriched(7).cancelled(false).build());

        scheduler.run();

        verify(triggerRequestQueue, times(1)).markRunning(id);
        verify(enrichJobsUseCase, times(1)).enrichPending(ENRICH_BATCH_SIZE, id, shutdownSignal);
        verify(triggerRequestQueue, times(1))
                .markDone(eq(id), eq("succeeded"), eq("completed"), eq("enriched 7 postings"), isNull());
        verify(crawlUseCase, never()).crawlBatch(anyInt(), any(), any());
    }

    @Test
    @DisplayName("CR-U-03: marks row FAILED when CrawlUseCase throws")
    void marksRowFailedWhenCrawlUseCaseThrows() {
        UUID id = UUID.randomUUID();
        when(triggerRequestQueue.claimNext(TriggerKind.CRAWL)).thenReturn(Optional.of(queuedRequest(id, TriggerKind.CRAWL)));
        when(triggerRequestQueue.claimNext(TriggerKind.ENRICHMENT)).thenReturn(Optional.empty());
        when(crawlUseCase.crawlBatch(MIN_NEW_POSTS, id, shutdownSignal)).thenThrow(new RuntimeException("DB down"));

        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();

        verify(triggerRequestQueue, times(1)).markRunning(id);
        verify(triggerRequestQueue, times(1))
                .markDone(eq(id), eq("failed"), eq("failed"), isNull(), eq("DB down"));
    }

    @Test
    @DisplayName("CR-U-04: no-ops when no queued rows exist")
    void noOpsWhenNoQueuedRowsExist() {
        when(triggerRequestQueue.claimNext(TriggerKind.CRAWL)).thenReturn(Optional.empty());
        when(triggerRequestQueue.claimNext(TriggerKind.ENRICHMENT)).thenReturn(Optional.empty());

        scheduler.run();

        verify(triggerRequestQueue, never()).markRunning(any());
        verify(crawlUseCase, never()).crawlBatch(anyInt(), any(), any());
        verify(enrichJobsUseCase, never()).enrichPending(anyInt(), any(), any());
    }

    @Test
    @DisplayName("CR-U-05: claimNext is called once per kind per run, independently")
    void claimNextIsCalledOncePerKindIndependently() {
        UUID crawlId = UUID.randomUUID();
        when(triggerRequestQueue.claimNext(TriggerKind.CRAWL)).thenReturn(Optional.of(queuedRequest(crawlId, TriggerKind.CRAWL)));
        when(triggerRequestQueue.claimNext(TriggerKind.ENRICHMENT)).thenReturn(Optional.empty());
        when(crawlUseCase.crawlBatch(MIN_NEW_POSTS, crawlId, shutdownSignal)).thenReturn(
                CrawlBatchResult.builder().crawled(1).newPosts(15).hasMore(false).cancelled(false).build());

        scheduler.run();

        verify(triggerRequestQueue, times(1)).claimNext(TriggerKind.CRAWL);
        verify(triggerRequestQueue, times(1)).claimNext(TriggerKind.ENRICHMENT);
    }

    @Test
    @DisplayName("BE-ENR-01: enrichment is never claimed/processed while a crawl is running")
    void enrichmentNeverClaimedWhileCrawlRunning() {
        when(triggerRequestQueue.hasRunning(TriggerKind.CRAWL)).thenReturn(true);

        scheduler.run();

        verify(triggerRequestQueue, never()).claimNext(TriggerKind.ENRICHMENT);
        verify(enrichJobsUseCase, never()).enrichPending(anyInt(), any(), any());
    }

    @Test
    @DisplayName("BE-ENR-02: enrichment is claimed and processed normally when no crawl is running")
    void enrichmentClaimedWhenNoCrawlRunning() {
        UUID enrichmentId = UUID.randomUUID();
        when(triggerRequestQueue.claimNext(TriggerKind.CRAWL)).thenReturn(Optional.empty());
        when(triggerRequestQueue.hasRunning(TriggerKind.CRAWL)).thenReturn(false);
        when(triggerRequestQueue.claimNext(TriggerKind.ENRICHMENT))
                .thenReturn(Optional.of(queuedRequest(enrichmentId, TriggerKind.ENRICHMENT)));
        when(enrichJobsUseCase.enrichPending(ENRICH_BATCH_SIZE, enrichmentId, shutdownSignal)).thenReturn(
                EnrichBatchResult.builder().enriched(3).cancelled(false).build());

        scheduler.run();

        verify(triggerRequestQueue, times(1)).claimNext(TriggerKind.ENRICHMENT);
        verify(enrichJobsUseCase, times(1)).enrichPending(ENRICH_BATCH_SIZE, enrichmentId, shutdownSignal);
        verify(triggerRequestQueue, times(1))
                .markDone(eq(enrichmentId), eq("succeeded"), eq("completed"), eq("enriched 3 postings"), isNull());
    }

    @Test
    @DisplayName("BE-ENR-03 (story #398, N2): a second queued crawl is left queued -- claiming is "
            + "gated on hasRunning(CRAWL) just like enrichment, so the DB's one-active-run-per-kind "
            + "unique index (060) is never violated")
    void secondQueuedCrawlIsNotClaimedWhileOneIsRunning() {
        when(triggerRequestQueue.hasRunning(TriggerKind.CRAWL)).thenReturn(true);

        scheduler.run();

        verify(triggerRequestQueue, never()).claimNext(TriggerKind.CRAWL);
        verify(crawlUseCase, never()).crawlBatch(anyInt(), any(), any());
        verify(triggerRequestQueue, never()).claimNext(TriggerKind.ENRICHMENT);
    }

    @Test
    @DisplayName("BE-ENR-04: no-op for enrichment when no crawl running and no enrichment queued")
    void noOpForEnrichmentWhenNoCrawlRunningAndNoneQueued() {
        when(triggerRequestQueue.claimNext(TriggerKind.CRAWL)).thenReturn(Optional.empty());
        when(triggerRequestQueue.hasRunning(TriggerKind.CRAWL)).thenReturn(false);
        when(triggerRequestQueue.claimNext(TriggerKind.ENRICHMENT)).thenReturn(Optional.empty());

        scheduler.run();

        verify(enrichJobsUseCase, never()).enrichPending(anyInt(), any(), any());
    }

    @Test
    @DisplayName("BE-ENR-05 (story #398, N2): both crawl claiming and enrichment are skipped while a crawl is running")
    void crawlAndEnrichmentBothSkippedWhenCrawlRunning() {
        when(triggerRequestQueue.hasRunning(TriggerKind.CRAWL)).thenReturn(true);

        scheduler.run();

        verify(triggerRequestQueue, never()).claimNext(TriggerKind.CRAWL);
        verify(crawlUseCase, never()).crawlBatch(anyInt(), any(), any());
        verify(triggerRequestQueue, never()).claimNext(TriggerKind.ENRICHMENT);
        verify(enrichJobsUseCase, never()).enrichPending(anyInt(), any(), any());
    }

    @Test
    @DisplayName("BE-ENR-06: crawl claiming and enrichment are both conservatively skipped (fail-safe) when hasRunning throws")
    void enrichmentSkippedWhenHasRunningThrows() {
        when(triggerRequestQueue.hasRunning(TriggerKind.CRAWL)).thenThrow(new RuntimeException("DB error"));

        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();

        verify(triggerRequestQueue, never()).claimNext(TriggerKind.CRAWL);
        verify(triggerRequestQueue, never()).claimNext(TriggerKind.ENRICHMENT);
        verify(enrichJobsUseCase, never()).enrichPending(anyInt(), any(), any());
    }

    @Test
    @DisplayName("CS-U-13: TriggerRequestScheduler passes minNewPosts (not old batch-size) to crawlBatch")
    void passesMinNewPostsToCrawlBatch() {
        UUID id = UUID.randomUUID();
        scheduler.minNewPosts = 100;
        when(triggerRequestQueue.claimNext(TriggerKind.CRAWL)).thenReturn(Optional.of(queuedRequest(id, TriggerKind.CRAWL)));
        when(triggerRequestQueue.claimNext(TriggerKind.ENRICHMENT)).thenReturn(Optional.empty());
        when(crawlUseCase.crawlBatch(100, id, shutdownSignal)).thenReturn(
                CrawlBatchResult.builder().crawled(7).newPosts(105).hasMore(false).cancelled(false).build());

        scheduler.run();

        verify(crawlUseCase, times(1)).crawlBatch(100, id, shutdownSignal);
    }

    @Test
    @DisplayName("CS-U-14a: completion summary includes both crawled (targets) and newPosts on success")
    void completionSummaryIncludesBothTargetsAndNewPosts() {
        UUID id = UUID.randomUUID();
        when(triggerRequestQueue.claimNext(TriggerKind.CRAWL)).thenReturn(Optional.of(queuedRequest(id, TriggerKind.CRAWL)));
        when(triggerRequestQueue.claimNext(TriggerKind.ENRICHMENT)).thenReturn(Optional.empty());
        when(crawlUseCase.crawlBatch(MIN_NEW_POSTS, id, shutdownSignal)).thenReturn(
                CrawlBatchResult.builder().crawled(7).newPosts(105).hasMore(false).cancelled(false).build());

        scheduler.run();

        verify(triggerRequestQueue, times(1))
                .markDone(eq(id), eq("succeeded"), eq("completed"),
                        argThat(s -> s.contains("7") && s.contains("105")),
                        isNull());
        verify(triggerRequestQueue, never()).markCancelled(eq(id), any());
    }

    @Test
    @DisplayName("CS-U-14b: cancellation summary includes both crawled (targets) and newPosts")
    void cancellationSummaryIncludesBothTargetsAndNewPosts() {
        UUID id = UUID.randomUUID();
        when(triggerRequestQueue.claimNext(TriggerKind.CRAWL)).thenReturn(Optional.of(queuedRequest(id, TriggerKind.CRAWL)));
        when(triggerRequestQueue.claimNext(TriggerKind.ENRICHMENT)).thenReturn(Optional.empty());
        when(crawlUseCase.crawlBatch(MIN_NEW_POSTS, id, shutdownSignal)).thenReturn(
                CrawlBatchResult.builder().crawled(3).newPosts(30).hasMore(false).cancelled(true).build());

        scheduler.run();

        verify(triggerRequestQueue, times(1))
                .markCancelled(eq(id),
                        argThat(s -> s.contains("3") && s.contains("30")));
        verify(triggerRequestQueue, never()).markDone(eq(id), any(), any(), any(), any());
    }

    @Test
    @DisplayName("CS-UNIT-03: calls markCancelled (not markDone) after a cancelled crawl batch")
    void marksCancelledAfterCancelledCrawlBatch() {
        UUID id = UUID.randomUUID();
        when(triggerRequestQueue.claimNext(TriggerKind.CRAWL)).thenReturn(Optional.of(queuedRequest(id, TriggerKind.CRAWL)));
        when(triggerRequestQueue.claimNext(TriggerKind.ENRICHMENT)).thenReturn(Optional.empty());
        when(crawlUseCase.crawlBatch(MIN_NEW_POSTS, id, shutdownSignal)).thenReturn(
                CrawlBatchResult.builder().crawled(3).newPosts(0).hasMore(false).cancelled(true).build());

        scheduler.run();

        verify(triggerRequestQueue, times(1)).markRunning(id);
        verify(triggerRequestQueue, times(1))
                .markCancelled(eq(id), argThat(s -> s.contains("3") && s.contains("0")));
        verify(triggerRequestQueue, never()).markDone(eq(id), any(), any(), any(), any());
    }

    @Test
    @DisplayName("CS-UNIT-04: calls markCancelled with 'postings' wording after a cancelled enrichment batch")
    void marksCancelledAfterCancelledEnrichmentBatch() {
        UUID id = UUID.randomUUID();
        when(triggerRequestQueue.claimNext(TriggerKind.CRAWL)).thenReturn(Optional.empty());
        when(triggerRequestQueue.claimNext(TriggerKind.ENRICHMENT)).thenReturn(Optional.of(queuedRequest(id, TriggerKind.ENRICHMENT)));
        scheduler.enrichBatchSize = 40;
        when(enrichJobsUseCase.enrichPending(40, id, shutdownSignal)).thenReturn(
                EnrichBatchResult.builder().enriched(12).cancelled(true).build());

        scheduler.run();

        verify(triggerRequestQueue, times(1))
                .markCancelled(eq(id), argThat(s -> s.contains("12") && s.contains("40") && s.toLowerCase().contains("posting")));
        verify(triggerRequestQueue, never()).markDone(eq(id), any(), any(), any(), any());
    }

    @Test
    @DisplayName("CS-UNIT-05: still calls markDone(\"succeeded\", ...) when the batch completes without cancellation")
    void marksDoneSucceededWhenBatchNotCancelled() {
        UUID id = UUID.randomUUID();
        when(triggerRequestQueue.claimNext(TriggerKind.CRAWL)).thenReturn(Optional.of(queuedRequest(id, TriggerKind.CRAWL)));
        when(triggerRequestQueue.claimNext(TriggerKind.ENRICHMENT)).thenReturn(Optional.empty());
        when(crawlUseCase.crawlBatch(MIN_NEW_POSTS, id, shutdownSignal)).thenReturn(
                CrawlBatchResult.builder().crawled(5).newPosts(75).hasMore(false).cancelled(false).build());

        scheduler.run();

        verify(triggerRequestQueue, times(1))
                .markDone(eq(id), eq("succeeded"), eq("completed"), argThat(s -> s.contains("5") && s.contains("75")), isNull());
        verify(triggerRequestQueue, never()).markCancelled(eq(id), any());
    }

    @Test
    @DisplayName("C18: admin-triggered enrichment bypasses the backoff check (D4), still reports the outcome")
    void adminTriggeredEnrichmentBypassesBackoffCheck() {
        UUID id = UUID.randomUUID();
        when(triggerRequestQueue.claimNext(TriggerKind.CRAWL)).thenReturn(Optional.empty());
        when(triggerRequestQueue.claimNext(TriggerKind.ENRICHMENT))
                .thenReturn(Optional.of(queuedRequest(id, TriggerKind.ENRICHMENT)));
        when(enrichJobsUseCase.enrichPending(ENRICH_BATCH_SIZE, id, shutdownSignal)).thenReturn(
                EnrichBatchResult.builder().attempted(5).enriched(0).cancelled(false).build());

        scheduler.run();

        verify(enrichJobsUseCase, times(1)).enrichPending(ENRICH_BATCH_SIZE, id, shutdownSignal);
        verify(backoffState, never()).isBackedOff(any());
        verify(backoffState, times(1)).onPassResult(eq(5), eq(0), eq(false), any());
    }

    @Test
    @DisplayName("C3: shutdown in progress -- run() returns at once with zero repository calls")
    void shutdownInProgressReturnsAtOnceWithZeroRepoCalls() {
        when(shutdownSignal.isShuttingDown()).thenReturn(true);

        scheduler.run();

        verify(triggerRequestQueue, never()).claimNext(any());
        verify(triggerRequestQueue, never()).hasRunning(any());
        verify(crawlUseCase, never()).crawlBatch(anyInt(), any(), any());
        verify(enrichJobsUseCase, never()).enrichPending(anyInt(), any(), any());
    }

    @Test
    @DisplayName("story #398, D1, 4th pass: execute()'s catch performs NO repository write once "
            + "the shutdown flag is up, even though the batch itself threw -- the reap on the way "
            + "down already finalized the row, and a second write is guaranteed to fail once the "
            + "CDI container is gone")
    void executeDoesNoRepositoryWriteOnceShutdownFlagIsUpEvenAfterABatchFailure() throws Exception {
        UUID id = UUID.randomUUID();
        when(triggerRequestQueue.claimNext(TriggerKind.CRAWL))
                .thenReturn(Optional.of(queuedRequest(id, TriggerKind.CRAWL)));
        when(triggerRequestQueue.claimNext(TriggerKind.ENRICHMENT)).thenReturn(Optional.empty());
        // Stands in for a drain-timeout interrupt landing mid-batch: the batch call itself
        // throws (e.g. because its own internal writes hit a torn-down CDI container).
        when(crawlUseCase.crawlBatch(MIN_NEW_POSTS, id, shutdownSignal))
                .thenThrow(new IllegalStateException("ArC container not initialized"));
        ShutdownFlag.raise();

        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();

        verify(triggerRequestQueue, times(1)).markRunning(id);
        verify(triggerRequestQueue, never()).markDone(any(), any(), any(), any(), any());
        verify(triggerRequestQueue, never()).markCancelled(any(), any());
    }
}
