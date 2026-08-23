package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.TriggerRequestEntity;
import com.davidcreate.jobhub.crawler.component_tests.support.ShutdownDrainIsolationProfile;
import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownFlag;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story #398 (ADR 0032, D1, reopened): a real `podman compose down` reproduced
 * "[Error Occurred After Shutdown]" because setting the flag alone did not block teardown
 * while a crawl was still executing on a worker thread. This test fires the actual Quarkus
 * {@code ShutdownEvent} (not a mocked flag) while work is genuinely in flight (tracked via
 * the real {@code ShutdownSignal.workStarted()}/{@code workFinished()} contract that
 * {@code CrawlerService}/{@code EnrichmentService} call around every batch) and proves the
 * observer actually blocks until that work exits, then marks the still-non-terminal
 * {@code trigger_request} row interrupted before returning -- while the datasource is still
 * alive, not on the next process start.
 */
@QuarkusTest
@TestProfile(ShutdownDrainIsolationProfile.class)
@DisplayName("Shutdown Drain Component Tests")
class ShutdownDrainComponentTest {

    @Inject
    Event<ShutdownEvent> shutdownEvent;

    @Inject
    ShutdownSignal shutdownSignal;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void clearTriggerRequests() {
        QuarkusTransaction.requiringNew().run(() ->
                entityManager.createQuery("delete from TriggerRequestEntity").executeUpdate());
    }

    @AfterEach
    void resetShutdownFlag() throws Exception {
        // ShutdownFlag is a plain static, deliberately CDI-free (ADR 0032, story #398, D1, 4th
        // pass), so it survives even a Quarkus application-context restart between test
        // profiles within the same JVM fork. Firing a real ShutdownEvent here raises it for
        // real; reset it via reflection so it never leaks into a sibling test class. No
        // production reset method exists on purpose: real shutdown is never un-raised.
        Field flagField = ShutdownFlag.class.getDeclaredField("shuttingDown");
        flagField.setAccessible(true);
        flagField.set(null, false);
    }

    private UUID insertRunningRow() {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            TriggerRequestEntity entity = new TriggerRequestEntity();
            entity.id = id;
            entity.kind = TriggerKind.CRAWL.value();
            entity.status = "running";
            entity.requestedAt = OffsetDateTime.now().minusMinutes(1);
            entity.startedAt = OffsetDateTime.now().minusMinutes(1);
            entityManager.persist(entity);
        });
        return id;
    }

    private TriggerRequestEntity findEntity(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.find(TriggerRequestEntity.class, id));
    }

    @Test
    @DisplayName("real ShutdownEvent blocks until in-flight work exits, then reaps the row that "
            + "was still running (D1/D2: the observer is the one that both drains and marks terminal)")
    void shutdownEventDrainsLiveInFlightWorkThenReapsTheStillRunningRow() throws Exception {
        UUID id = insertRunningRow();

        CountDownLatch workStarted = new CountDownLatch(1);
        CountDownLatch releaseWork = new CountDownLatch(1);

        // Stands in for a crawl/enrichment batch executing on its own worker thread: it
        // registers itself in flight via the real port contract, exactly as CrawlerService/
        // EnrichmentService do around their loops, and never updates its own trigger_request
        // row before finishing -- reproducing the original bug's timing window.
        Thread worker = new Thread(() -> {
            shutdownSignal.workStarted();
            workStarted.countDown();
            try {
                releaseWork.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                shutdownSignal.workFinished();
            }
        }, "in-flight-crawl-worker");
        worker.start();

        assertThat(workStarted.await(2, TimeUnit.SECONDS)).isTrue();

        Thread shutdownThread = new Thread(() -> shutdownEvent.fire(new ShutdownEvent()), "shutdown-event-fire");
        shutdownThread.start();

        // The observer must genuinely block here: work is still in flight.
        Thread.sleep(300);
        assertThat(shutdownThread.isAlive()).isTrue();
        assertThat(shutdownSignal.isShuttingDown()).isTrue();

        releaseWork.countDown();
        worker.join(2000);
        shutdownThread.join(5000);
        assertThat(shutdownThread.isAlive()).isFalse();

        TriggerRequestEntity entity = findEntity(id);
        assertThat(entity.status).isEqualTo("failed");
        assertThat(entity.outcome).isEqualTo("interrupted");
        assertThat(entity.errorReason).containsIgnoringCase("shutdown");
    }
}
