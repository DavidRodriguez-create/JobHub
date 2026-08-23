package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.adapter.in.scheduler.TriggerRequestScheduler;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.TriggerRequestEntity;
import com.davidcreate.jobhub.crawler.domain.model.CrawlBatchResult;
import com.davidcreate.jobhub.crawler.domain.model.EnrichBatchResult;
import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.port.in.CrawlUseCase;
import com.davidcreate.jobhub.crawler.domain.port.in.EnrichJobsUseCase;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Component tests for {@link TriggerRequestScheduler} driving real DB persistence
 * via {@code TriggerRequestPanacheRepository}. {@code CrawlUseCase}/{@code EnrichJobsUseCase}
 * are mocked to avoid real network calls. The scheduler's {@code run()} is invoked
 * directly (FLAG-3: no wall-clock waits, {@code quarkus.scheduler.enabled=false}).
 */
@QuarkusTest
@DisplayName("TriggerRequestScheduler Component Tests")
class TriggerRequestSchedulerComponentTest {

    @Inject
    TriggerRequestScheduler scheduler;

    @Inject
    EntityManager entityManager;

    @InjectMock
    CrawlUseCase crawlUseCase;

    @InjectMock
    EnrichJobsUseCase enrichJobsUseCase;

    @BeforeEach
    void clearTriggerRequests() {
        QuarkusTransaction.requiringNew().run(() ->
                entityManager.createQuery("delete from TriggerRequestEntity").executeUpdate());
    }

    private UUID insertQueuedRow(TriggerKind kind, OffsetDateTime requestedAt) {
        return insertRow(kind, "queued", requestedAt);
    }

    private UUID insertRow(TriggerKind kind, String status, OffsetDateTime requestedAt) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            TriggerRequestEntity entity = new TriggerRequestEntity();
            entity.id = id;
            entity.kind = kind.value();
            entity.status = status;
            entity.requestedAt = requestedAt;
            if ("running".equals(status) || "succeeded".equals(status) || "failed".equals(status)) {
                entity.startedAt = requestedAt;
            }
            if ("succeeded".equals(status) || "failed".equals(status)) {
                entity.finishedAt = requestedAt.plusMinutes(5);
            }
            entityManager.persist(entity);
        });
        return id;
    }

    private TriggerRequestEntity findEntity(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.find(TriggerRequestEntity.class, id));
    }

    @Test
    @DisplayName("CR-C-04: scheduler claims queued crawl row, executes crawl, and transitions to succeeded")
    void schedulerClaimsCrawlRowAndTransitionsToSucceeded() {
        UUID id = insertQueuedRow(TriggerKind.CRAWL, OffsetDateTime.now().minusDays(3));
        when(crawlUseCase.crawlBatch(anyInt(), any(), any())).thenReturn(CrawlBatchResult.builder().crawled(3).hasMore(false).cancelled(false).build());

        scheduler.run();

        TriggerRequestEntity entity = findEntity(id);
        assertThat(entity.status).isEqualTo("succeeded");
        assertThat(entity.startedAt).isNotNull();
        assertThat(entity.finishedAt).isNotNull();
        assertThat(entity.resultSummary).contains("3");
        verify(crawlUseCase).crawlBatch(anyInt(), any(), any());
    }

    @Test
    @DisplayName("CR-C-05: scheduler marks row failed when CrawlUseCase throws")
    void schedulerMarksRowFailedWhenUseCaseThrows() {
        UUID id = insertQueuedRow(TriggerKind.CRAWL, OffsetDateTime.now().minusDays(4));
        when(crawlUseCase.crawlBatch(anyInt(), any(), any())).thenThrow(new RuntimeException("boom"));

        scheduler.run();

        TriggerRequestEntity entity = findEntity(id);
        assertThat(entity.status).isEqualTo("failed");
        assertThat(entity.errorReason).isNotNull();
    }

    @Test
    @DisplayName("CR-C-06: scheduler processes crawl and enrichment independently in the same run")
    void schedulerProcessesCrawlAndEnrichmentIndependently() {
        UUID crawlId = insertQueuedRow(TriggerKind.CRAWL, OffsetDateTime.now().minusDays(5));
        UUID enrichmentId = insertQueuedRow(TriggerKind.ENRICHMENT, OffsetDateTime.now().minusDays(5));
        when(crawlUseCase.crawlBatch(anyInt(), any(), any())).thenReturn(CrawlBatchResult.builder().crawled(2).hasMore(false).cancelled(false).build());
        when(enrichJobsUseCase.enrichPending(anyInt(), any(), any())).thenReturn(EnrichBatchResult.builder().enriched(4).cancelled(false).build());

        scheduler.run();

        TriggerRequestEntity crawlEntity = findEntity(crawlId);
        TriggerRequestEntity enrichmentEntity = findEntity(enrichmentId);

        assertThat(crawlEntity.status).isEqualTo("succeeded");
        assertThat(enrichmentEntity.status).isEqualTo("succeeded");
        verify(crawlUseCase).crawlBatch(anyInt(), any(), any());
        verify(enrichJobsUseCase).enrichPending(anyInt(), any(), any());
    }

    @Test
    @DisplayName("BE-ENR-07: enrichment row stays queued and enrichPending is never invoked while a crawl is running")
    void enrichmentStaysQueuedWhileCrawlRunning() {
        insertRow(TriggerKind.CRAWL, "running", OffsetDateTime.now().minusMinutes(5));
        UUID enrichmentId = insertQueuedRow(TriggerKind.ENRICHMENT, OffsetDateTime.now().minusDays(1));

        scheduler.run();

        TriggerRequestEntity enrichmentEntity = findEntity(enrichmentId);
        assertThat(enrichmentEntity.status).isEqualTo("queued");
        verify(enrichJobsUseCase, never()).enrichPending(anyInt(), any(), any());
    }

    @Test
    @DisplayName("BE-ENR-08: enrichment transitions normally when no crawl is running")
    void enrichmentTransitionsNormallyWhenNoCrawlRunning() {
        UUID enrichmentId = insertQueuedRow(TriggerKind.ENRICHMENT, OffsetDateTime.now().minusDays(1));
        when(enrichJobsUseCase.enrichPending(anyInt(), any(), any())).thenReturn(EnrichBatchResult.builder().enriched(2).cancelled(false).build());

        scheduler.run();

        TriggerRequestEntity enrichmentEntity = findEntity(enrichmentId);
        assertThat(enrichmentEntity.status).isEqualTo("succeeded");
        verify(enrichJobsUseCase).enrichPending(anyInt(), any(), any());
    }

    @Test
    @DisplayName("BE-ENR-09 (story #398, N2): running crawl + queued crawl + queued enrichment -> the "
            + "queued crawl waits (claiming is gated on hasRunning(CRAWL), same as enrichment), enrichment "
            + "also stays queued")
    void queuedCrawlAndEnrichmentBothStayQueuedWhenCrawlRunning() {
        insertRow(TriggerKind.CRAWL, "running", OffsetDateTime.now().minusMinutes(5));
        UUID queuedCrawlId = insertQueuedRow(TriggerKind.CRAWL, OffsetDateTime.now().minusDays(2));
        UUID enrichmentId = insertQueuedRow(TriggerKind.ENRICHMENT, OffsetDateTime.now().minusDays(1));

        scheduler.run();

        TriggerRequestEntity queuedCrawlEntity = findEntity(queuedCrawlId);
        TriggerRequestEntity enrichmentEntity = findEntity(enrichmentId);

        assertThat(queuedCrawlEntity.status).isEqualTo("queued");
        assertThat(enrichmentEntity.status).isEqualTo("queued");
        verify(crawlUseCase, never()).crawlBatch(anyInt(), any(), any());
        verify(enrichJobsUseCase, never()).enrichPending(anyInt(), any(), any());
    }

    @Test
    @DisplayName("C13: crawl active, admin manual trigger -> accepted and recorded queued (origin=manual), "
            + "not rejected, and it is left untouched until the active run finishes")
    void manualCrawlTriggerWhileCrawlActiveIsAcceptedAsQueued() {
        insertRow(TriggerKind.CRAWL, "running", OffsetDateTime.now().minusMinutes(5));
        UUID manualCrawlId = insertQueuedRow(TriggerKind.CRAWL, OffsetDateTime.now().minusDays(1));

        scheduler.run();

        TriggerRequestEntity manualCrawlEntity = findEntity(manualCrawlId);
        assertThat(manualCrawlEntity.status).isEqualTo("queued");
        assertThat(manualCrawlEntity.origin).isEqualTo("manual");
        verify(crawlUseCase, never()).crawlBatch(anyInt(), any(), any());
    }

    @Test
    @DisplayName("BE-ENR-10: running crawl + historical succeeded crawl + queued enrichment -> enrichment stays queued")
    void enrichmentStaysQueuedWhenAnyCrawlRunningRegardlessOfHistory() {
        insertRow(TriggerKind.CRAWL, "succeeded", OffsetDateTime.now().minusDays(10));
        insertRow(TriggerKind.CRAWL, "running", OffsetDateTime.now().minusMinutes(5));
        UUID enrichmentId = insertQueuedRow(TriggerKind.ENRICHMENT, OffsetDateTime.now().minusDays(1));

        scheduler.run();

        TriggerRequestEntity enrichmentEntity = findEntity(enrichmentId);
        assertThat(enrichmentEntity.status).isEqualTo("queued");
        verify(enrichJobsUseCase, never()).enrichPending(anyInt(), any(), any());
    }

    @Test
    @DisplayName("BE-ENR-11: no crawl rows at all + queued enrichment -> enrichment processes normally")
    void enrichmentProcessesNormallyWhenNoCrawlRowsExist() {
        UUID enrichmentId = insertQueuedRow(TriggerKind.ENRICHMENT, OffsetDateTime.now().minusDays(1));
        when(enrichJobsUseCase.enrichPending(anyInt(), any(), any())).thenReturn(EnrichBatchResult.builder().enriched(1).cancelled(false).build());

        scheduler.run();

        TriggerRequestEntity enrichmentEntity = findEntity(enrichmentId);
        assertThat(enrichmentEntity.status).isEqualTo("succeeded");
        verify(enrichJobsUseCase).enrichPending(anyInt(), any(), any());
    }

    @Test
    @DisplayName("CS-COMP-04: a cancelled crawl batch is finalized via markCancelled, not markDone")
    void cancelledCrawlBatchIsFinalizedViaMarkCancelled() {
        UUID id = insertQueuedRow(TriggerKind.CRAWL, OffsetDateTime.now().minusDays(3));
        when(crawlUseCase.crawlBatch(anyInt(), any(), any())).thenReturn(CrawlBatchResult.builder().crawled(1).hasMore(false).cancelled(true).build());

        scheduler.run();

        TriggerRequestEntity entity = findEntity(id);
        assertThat(entity.status).isEqualTo("cancelled");
        assertThat(entity.finishedAt).isNotNull();
        assertThat(entity.resultSummary).contains("1");
        assertThat(entity.status).isNotEqualTo("succeeded");
        assertThat(entity.status).isNotEqualTo("failed");
    }

    @Test
    @DisplayName("C8: a CRAWL row already reaped to terminal no longer blocks the ENRICHMENT poll")
    void reapedCrawlRowNoLongerBlocksEnrichmentPolling() {
        insertRow(TriggerKind.CRAWL, "failed", OffsetDateTime.now().minusHours(3));
        UUID enrichmentId = insertQueuedRow(TriggerKind.ENRICHMENT, OffsetDateTime.now().minusDays(1));
        when(enrichJobsUseCase.enrichPending(anyInt(), any(), any()))
                .thenReturn(EnrichBatchResult.builder().enriched(2).cancelled(false).build());

        scheduler.run();

        TriggerRequestEntity enrichmentEntity = findEntity(enrichmentId);
        assertThat(enrichmentEntity.status).isEqualTo("succeeded");
        verify(enrichJobsUseCase).enrichPending(anyInt(), any(), any());
    }

    @Test
    @DisplayName("C10: no targets eligible, admin crawl runs end to end -> status=succeeded, outcome=no_targets")
    void noTargetsCrawlRunsEndToEndAsSucceededWithNoTargetsOutcome() {
        UUID id = insertQueuedRow(TriggerKind.CRAWL, OffsetDateTime.now().minusDays(1));
        when(crawlUseCase.crawlBatch(anyInt(), any(), any())).thenReturn(
                CrawlBatchResult.builder().crawled(0).newPosts(0).hasMore(false).cancelled(false)
                        .outcome(com.davidcreate.jobhub.crawler.domain.model.TriggerOutcome.NO_TARGETS).build());

        scheduler.run();

        TriggerRequestEntity entity = findEntity(id);
        assertThat(entity.status).isEqualTo("succeeded");
        assertThat(entity.outcome).isEqualTo("no_targets");
        assertThat(entity.resultSummary).isEqualTo("no more targets to crawl");
    }
}
