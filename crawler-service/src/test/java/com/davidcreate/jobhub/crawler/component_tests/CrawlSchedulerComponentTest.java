package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.adapter.in.scheduler.CrawlerScheduler;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.TriggerRequestEntity;
import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story #398 (ADR 0032, N2, AC7): {@code CrawlerScheduler} no longer crawls directly -- it
 * enqueues a {@code trigger_request} row (origin = scheduled) only when no crawl run is
 * already active, yielding its own tick otherwise so the scheduled pass never collides with
 * a manual (or another scheduled) run.
 */
@QuarkusTest
@DisplayName("CrawlerScheduler Component Tests")
class CrawlSchedulerComponentTest {

    @Inject
    CrawlerScheduler scheduler;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void clearTriggerRequests() {
        QuarkusTransaction.requiringNew().run(() ->
                entityManager.createQuery("delete from TriggerRequestEntity").executeUpdate());
    }

    private void insertRunningCrawl() {
        QuarkusTransaction.requiringNew().run(() -> {
            TriggerRequestEntity entity = new TriggerRequestEntity();
            entity.id = java.util.UUID.randomUUID();
            entity.kind = TriggerKind.CRAWL.value();
            entity.status = "running";
            entity.requestedAt = OffsetDateTime.now().minusMinutes(5);
            entity.startedAt = OffsetDateTime.now().minusMinutes(5);
            entityManager.persist(entity);
        });
    }

    private long crawlRowCount() {
        return QuarkusTransaction.requiringNew()
                .call(() -> TriggerRequestEntity.count("kind", TriggerKind.CRAWL.value()));
    }

    @Test
    @DisplayName("C12: an active running crawl -- the scheduled tick skips, CRAWL row count is unchanged")
    void activeRunningCrawlLeavesRowCountUnchanged() {
        insertRunningCrawl();
        long before = crawlRowCount();

        scheduler.run();

        long after = crawlRowCount();
        assertThat(after).isEqualTo(before);
    }
}
