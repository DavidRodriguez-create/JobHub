package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.TriggerRequestEntity;
import com.davidcreate.jobhub.crawler.domain.model.CrawlProgress;
import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.model.TriggerRequest;
import com.davidcreate.jobhub.crawler.domain.port.out.CrawlProgressRecorder;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Component tests for {@link com.davidcreate.jobhub.crawler.adapter.out.persistence.TriggerRequestPanacheRepository}
 * against a real DevServices Postgres. Each test inserts its own row(s) with a
 * far-in-the-past {@code requested_at} so it is always the oldest queued row of
 * its kind, regardless of leftover rows from other tests in this class.
 */
@QuarkusTest
@DisplayName("TriggerRequestPanacheRepository Component Tests")
class TriggerRequestPanacheRepositoryComponentTest {

    @Inject
    TriggerRequestQueue triggerRequestQueue;

    @Inject
    CrawlProgressRecorder crawlProgressRecorder;

    @Inject
    EntityManager entityManager;

    // Story #398 (ADR 0032, N2): uq_trigger_request_active_kind_status now allows at most one
    // queued + one running row per kind at a time, so leftover active rows from a previous test
    // in this class (this class never called markDone/markCancelled on every row it seeded) can
    // no longer be left lying around uncleaned.
    @BeforeEach
    void clearTriggerRequests() {
        QuarkusTransaction.requiringNew().run(() ->
                entityManager.createQuery("delete from TriggerRequestEntity").executeUpdate());
    }

    private UUID insertRow(TriggerKind kind, String status, OffsetDateTime requestedAt) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            TriggerRequestEntity entity = new TriggerRequestEntity();
            entity.id = id;
            entity.kind = kind.value();
            entity.status = status;
            entity.requestedAt = requestedAt;
            if ("running".equals(status) || "cancel_requested".equals(status)) {
                entity.startedAt = OffsetDateTime.now().minusMinutes(1);
            }
            entityManager.persist(entity);
        });
        return id;
    }

    private TriggerRequestEntity findEntity(UUID id) {
        return entityManager.find(TriggerRequestEntity.class, id);
    }

    @Test
    @DisplayName("CR-C-01: claimNext returns the queued row for its own kind, leaving a queued row of "
            + "the other kind untouched (story #398, N2: uq_trigger_request_active_kind_status now allows "
            + "at most one queued row per kind, so the pre-#398 same-kind ordering scenario is no longer "
            + "a reachable state -- claimNext's own ORDER BY requested_at LIMIT 1 still applies were it ever hit)")
    void claimNextReturnsQueuedRowForItsOwnKindOnly() {
        UUID crawlId = insertRow(TriggerKind.CRAWL, "queued", OffsetDateTime.now().minusDays(2).minusMinutes(1));
        UUID enrichmentId = insertRow(TriggerKind.ENRICHMENT, "queued", OffsetDateTime.now().minusDays(2));

        Optional<TriggerRequest> claimed = triggerRequestQueue.claimNext(TriggerKind.CRAWL);

        assertThat(claimed).isPresent();
        assertThat(claimed.get().getId()).isEqualTo(crawlId);

        TriggerRequestEntity claimedEntity = findEntity(crawlId);
        assertThat(claimedEntity.status).isEqualTo("running");

        TriggerRequestEntity untouchedEntity = findEntity(enrichmentId);
        assertThat(untouchedEntity.status).isEqualTo("queued");
    }

    @Test
    @DisplayName("CR-C-02: markDone transitions row to succeeded with summary")
    void markDoneTransitionsToSucceededWithSummary() {
        UUID id = insertRow(TriggerKind.CRAWL, "running", OffsetDateTime.now().minusMinutes(5));

        triggerRequestQueue.markDone(id, "succeeded", "completed", "crawled 3 targets, 10 new postings", null);

        TriggerRequestEntity entity = findEntity(id);
        assertThat(entity.status).isEqualTo("succeeded");
        assertThat(entity.startedAt).isNotNull();
        assertThat(entity.finishedAt).isNotNull();
        assertThat(entity.resultSummary).isEqualTo("crawled 3 targets, 10 new postings");
        assertThat(entity.errorReason).isNull();
    }

    @Test
    @DisplayName("CR-C-03: markDone transitions row to failed with errorReason")
    void markDoneTransitionsToFailedWithErrorReason() {
        UUID id = insertRow(TriggerKind.ENRICHMENT, "running", OffsetDateTime.now().minusMinutes(5));

        triggerRequestQueue.markDone(id, "failed", "failed", null, "connection refused");

        TriggerRequestEntity entity = findEntity(id);
        assertThat(entity.status).isEqualTo("failed");
        assertThat(entity.errorReason).isEqualTo("connection refused");
        assertThat(entity.resultSummary).isNull();
    }

    @Test
    @DisplayName("CS-COMP-01: isCancelRequested returns true for a cancel_requested row")
    void isCancelRequestedReturnsTrueForCancelRequestedRow() {
        UUID id = insertRow(TriggerKind.CRAWL, "cancel_requested", OffsetDateTime.now().minusMinutes(5));

        assertThat(triggerRequestQueue.isCancelRequested(id)).isTrue();
    }

    @ParameterizedTest(name = "CS-COMP-02/CS-UNIT-07: isCancelRequested returns false for {0} row")
    @ValueSource(strings = {"queued", "running", "succeeded", "failed", "cancelled"})
    void isCancelRequestedReturnsFalseForNonCancelRequestedStatus(String status) {
        UUID id = insertRow(TriggerKind.CRAWL, status, OffsetDateTime.now().minusMinutes(5));

        assertThat(triggerRequestQueue.isCancelRequested(id)).isFalse();
    }

    @Test
    @DisplayName("CS-COMP-03: markCancelled transitions a cancel_requested row to cancelled with a summary")
    void markCancelledTransitionsToCancelledWithSummary() {
        UUID id = insertRow(TriggerKind.CRAWL, "cancel_requested", OffsetDateTime.now().minusMinutes(5));

        triggerRequestQueue.markCancelled(id, "Cancelled after 3 of 10 targets");

        TriggerRequestEntity entity = findEntity(id);
        assertThat(entity.status).isEqualTo("cancelled");
        assertThat(entity.finishedAt).isNotNull();
        assertThat(entity.resultSummary).isEqualTo("Cancelled after 3 of 10 targets");
        assertThat(entity.errorReason).isNull();
    }

    // ─── CrawlProgressRecorder (ADR 0029, story #513) ──────────────────────────

    @Test
    @DisplayName("TC-513-B12: markCurrentTarget commits and is visible from a fresh transaction "
            + "while the caller's own transaction is still open (REQUIRES_NEW visibility)")
    void markCurrentTargetIsVisibleFromFreshTransactionWhileOuterStillOpen() {
        UUID id = insertRow(TriggerKind.CRAWL, "running", OffsetDateTime.now().minusMinutes(5));

        QuarkusTransaction.begin();
        try {
            crawlProgressRecorder.markCurrentTarget(id, "Klaviyo", "greenhouse");

            TriggerRequestEntity fresh = QuarkusTransaction.requiringNew().call(() -> findEntity(id));
            assertThat(fresh.progressCurrentCompany).isEqualTo("Klaviyo");
            assertThat(fresh.progressCurrentSourceType).isEqualTo("greenhouse");
            assertThat(fresh.progressUpdatedAt).isNotNull();
        } finally {
            QuarkusTransaction.rollback();
        }
    }

    @Test
    @DisplayName("TC-513-B13: progress survives when the caller's own transaction rolls back")
    void progressSurvivesCallerRollback() {
        UUID id = insertRow(TriggerKind.CRAWL, "running", OffsetDateTime.now().minusMinutes(5));

        QuarkusTransaction.begin();
        try {
            crawlProgressRecorder.markCurrentTarget(id, "Klaviyo", "greenhouse");
            crawlProgressRecorder.recordTargetCompleted(id, CrawlProgress.builder()
                    .targetsVisited(1)
                    .newPosts(6)
                    .lastCompanyName("Klaviyo")
                    .lastSourceType("greenhouse")
                    .lastFoundPosts(8)
                    .lastNewPosts(6)
                    .build());
        } finally {
            QuarkusTransaction.rollback();
        }

        TriggerRequestEntity entity = findEntity(id);
        assertThat(entity.progressTargetsVisited).isEqualTo(1);
        assertThat(entity.progressNewPosts).isEqualTo(6);
        assertThat(entity.progressLastCompany).isEqualTo("Klaviyo");
        assertThat(entity.progressLastFoundPosts).isEqualTo(8);
        assertThat(entity.progressLastNewPosts).isEqualTo(6);
    }

    @Test
    @DisplayName("TC-513-B14: writes are a no-op with no exception when triggerRequestId is null or unknown")
    void writesAreNoOpForNullOrUnknownId() {
        long before = QuarkusTransaction.requiringNew().call(() -> TriggerRequestEntity.count());

        crawlProgressRecorder.markCurrentTarget(null, "X", "y");
        crawlProgressRecorder.markCurrentTarget(UUID.randomUUID(), "X", "y");

        long after = QuarkusTransaction.requiringNew().call(() -> TriggerRequestEntity.count());
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("TC-513-B15: markRunning zeroes the counters and clears the current-target fields")
    void markRunningZeroesCountersAndClearsCurrentTarget() {
        UUID id = insertRow(TriggerKind.CRAWL, "queued", OffsetDateTime.now().minusMinutes(5));
        crawlProgressRecorder.markCurrentTarget(id, "Leftover", "lever");

        triggerRequestQueue.markRunning(id);

        TriggerRequestEntity entity = findEntity(id);
        assertThat(entity.progressTargetsVisited).isEqualTo(0);
        assertThat(entity.progressNewPosts).isEqualTo(0);
        assertThat(entity.progressCurrentCompany).isNull();
        assertThat(entity.progressCurrentSourceType).isNull();
    }

    @Test
    @DisplayName("TC-513-B16: markDone leaves the final counters but nulls the current-target fields")
    void markDoneLeavesCountersButNullsCurrentTarget() {
        UUID id = insertRow(TriggerKind.CRAWL, "running", OffsetDateTime.now().minusMinutes(5));
        crawlProgressRecorder.recordTargetCompleted(id, CrawlProgress.builder()
                .targetsVisited(9)
                .newPosts(103)
                .lastCompanyName("Figma")
                .lastSourceType("lever")
                .lastFoundPosts(20)
                .lastNewPosts(3)
                .build());
        crawlProgressRecorder.markCurrentTarget(id, "Figma", "lever");

        triggerRequestQueue.markDone(id, "succeeded", "completed", "Batch complete: 9 targets visited, 103 new posts", null);

        TriggerRequestEntity entity = findEntity(id);
        assertThat(entity.progressTargetsVisited).isEqualTo(9);
        assertThat(entity.progressNewPosts).isEqualTo(103);
        assertThat(entity.progressCurrentCompany).isNull();
        assertThat(entity.progressCurrentSourceType).isNull();
    }

    @Test
    @DisplayName("TC-513-B16: markCancelled leaves the final counters but nulls the current-target fields")
    void markCancelledLeavesCountersButNullsCurrentTarget() {
        UUID id = insertRow(TriggerKind.CRAWL, "cancel_requested", OffsetDateTime.now().minusMinutes(5));
        crawlProgressRecorder.recordTargetCompleted(id, CrawlProgress.builder()
                .targetsVisited(4)
                .newPosts(30)
                .lastCompanyName("Acme")
                .lastSourceType("greenhouse")
                .lastFoundPosts(10)
                .lastNewPosts(2)
                .build());
        crawlProgressRecorder.markCurrentTarget(id, "Acme", "greenhouse");

        triggerRequestQueue.markCancelled(id, "Batch cancelled: 4 targets visited, 30 new posts before stop");

        TriggerRequestEntity entity = findEntity(id);
        assertThat(entity.progressTargetsVisited).isEqualTo(4);
        assertThat(entity.progressNewPosts).isEqualTo(30);
        assertThat(entity.progressCurrentCompany).isNull();
        assertThat(entity.progressCurrentSourceType).isNull();
    }

    // ─── Active-run exclusivity (ADR 0032, story #398, N2) ─────────────────────

    @Test
    @DisplayName("C14: a running row plus one extra queued row of the same kind is allowed "
            + "(uq_trigger_request_active_kind_status permits one queued + one running per kind), "
            + "but a second queued row of that kind violates the unique index")
    void uniqueIndexAllowsOneExtraQueuedSlotAlongsideARunningRow() {
        insertRow(TriggerKind.CRAWL, "running", OffsetDateTime.now().minusMinutes(5));

        // The first extra queued row (alongside the running one) is allowed.
        UUID firstQueued = insertRow(TriggerKind.CRAWL, "queued", OffsetDateTime.now().minusDays(1));
        assertThat(findEntity(firstQueued)).isNotNull();

        // A second queued row of the same kind violates uq_trigger_request_active_kind_status.
        assertThatThrownBy(() -> insertRow(TriggerKind.CRAWL, "queued", OffsetDateTime.now().minusDays(2)))
                .isInstanceOf(RuntimeException.class);
    }
}
