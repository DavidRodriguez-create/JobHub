package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.adapter.in.scheduler.TriggerRequestReaper;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story #398 (ADR 0032, D2, AC4): the live sweep half of {@code TriggerRequestReaper} --
 * a {@code running} row older than {@code crawler.trigger.stale-after} is interrupted
 * without waiting for a restart. {@code sweepStale(now)} is the same logic the
 * {@code @Scheduled} tick calls; invoked directly here with an explicit {@code now}
 * (the module disables the scheduler in tests, per {@code quarkus.scheduler.enabled=false}).
 */
@QuarkusTest
@DisplayName("TriggerRequestReaper Sweep Component Tests")
class TriggerRequestReaperSweepComponentTest {

    @Inject
    TriggerRequestReaper reaper;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void clearTriggerRequests() {
        QuarkusTransaction.requiringNew().run(() ->
                entityManager.createQuery("delete from TriggerRequestEntity").executeUpdate());
    }

    private UUID insertRunning(TriggerKind kind, OffsetDateTime startedAt) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            TriggerRequestEntity entity = new TriggerRequestEntity();
            entity.id = id;
            entity.kind = kind.value();
            entity.status = "running";
            entity.requestedAt = startedAt;
            entity.startedAt = startedAt;
            entityManager.persist(entity);
        });
        return id;
    }

    private TriggerRequestEntity findEntity(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.find(TriggerRequestEntity.class, id));
    }

    @Test
    @DisplayName("C6: a running row older than the configured stale threshold is interrupted by the live sweep")
    void staleRunningRowIsInterruptedByTheSweep() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID id = insertRunning(TriggerKind.CRAWL, now.minusHours(3));

        reaper.sweepStale(now);

        TriggerRequestEntity entity = findEntity(id);
        assertThat(entity.status).isEqualTo("failed");
        assertThat(entity.outcome).isEqualTo("interrupted");
        assertThat(entity.errorReason).containsIgnoringCase("shutdown");
    }
}
