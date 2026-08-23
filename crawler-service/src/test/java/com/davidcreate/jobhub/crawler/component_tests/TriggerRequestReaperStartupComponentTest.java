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
 * Story #398 (ADR 0032, D2, AC3): the startup half of {@code TriggerRequestReaper} --
 * {@code reapNonTerminal()} is the same method the real {@code StartupEvent} observer calls;
 * invoked directly here (the observer itself already fired once before this test's own seed
 * data existed, since {@code @QuarkusTest} boots the application before any test method runs).
 */
@QuarkusTest
@DisplayName("TriggerRequestReaper Startup Component Tests")
class TriggerRequestReaperStartupComponentTest {

    @Inject
    TriggerRequestReaper reaper;

    @Inject
    EntityManager entityManager;

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
            if ("running".equals(status)) {
                entity.startedAt = requestedAt;
            }
            entityManager.persist(entity);
        });
        return id;
    }

    private TriggerRequestEntity findEntity(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.find(TriggerRequestEntity.class, id));
    }

    @Test
    @DisplayName("C5: a row left running by a process killed between claim and completion is reaped on startup")
    void runningRowIsReapedOnStartup() {
        UUID id = insertRow(TriggerKind.CRAWL, "running", OffsetDateTime.now().minusMinutes(5));

        reaper.reapNonTerminal();

        TriggerRequestEntity entity = findEntity(id);
        assertThat(entity.status).isEqualTo("failed");
        assertThat(entity.outcome).isEqualTo("interrupted");
        assertThat(entity.errorReason).containsIgnoringCase("shutdown");
        assertThat(entity.finishedAt).isNotNull();
    }
}
