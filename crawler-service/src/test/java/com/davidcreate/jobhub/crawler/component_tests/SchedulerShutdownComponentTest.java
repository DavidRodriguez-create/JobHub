package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.adapter.in.scheduler.CrawlerScheduler;
import com.davidcreate.jobhub.crawler.adapter.in.scheduler.EnrichmentScheduler;
import com.davidcreate.jobhub.crawler.adapter.in.scheduler.TriggerRequestScheduler;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.TriggerRequestEntity;
import com.davidcreate.jobhub.crawler.domain.port.in.EnrichJobsUseCase;
import com.davidcreate.jobhub.crawler.component_tests.support.ShutdownSignalIsolationProfile;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownFlag;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.test.InjectMock;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Story #398 (ADR 0032, D1/AC2): once the real Quarkus {@code ShutdownEvent} fires, the
 * shared {@code ShutdownSignal} adapter flips, and every scheduler's next tick returns
 * immediately with zero side effects -- no new {@code trigger_request} rows, no claims,
 * no enrichment calls.
 */
@QuarkusTest
@TestProfile(ShutdownSignalIsolationProfile.class)
@DisplayName("Scheduler Shutdown Component Tests")
class SchedulerShutdownComponentTest {

    @Inject
    Event<ShutdownEvent> shutdownEvent;

    @Inject
    CrawlerScheduler crawlerScheduler;

    @Inject
    EnrichmentScheduler enrichmentScheduler;

    @Inject
    TriggerRequestScheduler triggerRequestScheduler;

    @Inject
    ShutdownSignal shutdownSignal;

    @Inject
    EntityManager entityManager;

    @InjectMock
    EnrichJobsUseCase enrichJobsUseCase;

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

    @Test
    @DisplayName("C4: ShutdownEvent fired, then all three schedulers polled again -- no new rows, no repo/use-case calls")
    void noNewActivityAfterShutdownEventFires() {
        shutdownEvent.fire(new ShutdownEvent());
        assertThat(shutdownSignal.isShuttingDown()).isTrue();

        long before = QuarkusTransaction.requiringNew().call(() -> TriggerRequestEntity.count());

        crawlerScheduler.run();
        enrichmentScheduler.run();
        triggerRequestScheduler.run();

        long after = QuarkusTransaction.requiringNew().call(() -> TriggerRequestEntity.count());
        assertThat(after).isEqualTo(before);
        verify(enrichJobsUseCase, never()).enrichPending(anyInt(), any(), any());
    }
}
