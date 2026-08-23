package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.lifecycle;

import com.davidcreate.jobhub.crawler.adapter.out.lifecycle.ShutdownSignalAdapter;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownFlag;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Story #398 (ADR 0032, D1, reopened): the bounded drain in {@code ShutdownSignalAdapter}.
 * {@code shutdown()} is the same sequence the real {@code ShutdownEvent} observer calls (same
 * split as {@code TriggerRequestReaper#onStart}/{@code reapNonTerminal}); exercised directly
 * here for the drain-timeout branch, with the real observer path against a live in-flight run
 * covered by {@code ShutdownDrainComponentTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShutdownSignalAdapter Unit Tests")
class ShutdownSignalAdapterTest {

    @Mock
    TriggerRequestQueue triggerRequestQueue;

    ShutdownSignalAdapter adapter;

    private static final Duration DRAIN_TIMEOUT = Duration.ofMillis(100);

    @BeforeEach
    void setUp() throws Exception {
        adapter = new ShutdownSignalAdapter(triggerRequestQueue);
        Field field = ShutdownSignalAdapter.class.getDeclaredField("drainTimeout");
        field.setAccessible(true);
        field.set(adapter, DRAIN_TIMEOUT);
    }

    @AfterEach
    void resetShutdownFlag() throws Exception {
        // ShutdownFlag is a plain static (deliberately, so it stays readable without CDI --
        // ADR 0032, story #398, D1, 4th pass); reset it via reflection so this test never
        // leaks "shutdown raised" into a sibling test class sharing this JVM fork. No
        // production reset method exists on purpose: real shutdown is never un-raised.
        Field flagField = ShutdownFlag.class.getDeclaredField("shuttingDown");
        flagField.setAccessible(true);
        flagField.set(null, false);
    }

    @Test
    @DisplayName("drain-timeout branch: a worker thread that never calls workFinished() still lets "
            + "shutdown() return within the configured bound, having raised the flag, interrupted "
            + "the abandoned thread, and reaped whatever is still non-terminal")
    void shutdownInterruptsAbandonedWorkAndReturnsWithinDrainTimeout() throws Exception {
        CountDownLatch workStarted = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            adapter.workStarted();
            workStarted.countDown();
            // Never calls workFinished(): stands in for a crawl/enrichment call still blocked
            // on outbound HTTP/model I/O when the drain window expires.
            try {
                Thread.sleep(Duration.ofSeconds(30).toMillis());
            } catch (InterruptedException ignored) {
                // Expected: shutdown()'s drain-timeout branch interrupts this thread.
            }
        }, "abandoned-worker");
        worker.setDaemon(true);
        worker.start();
        assertThat(workStarted.await(2, TimeUnit.SECONDS)).isTrue();

        assertTimeout(Duration.ofSeconds(2), () -> adapter.shutdown());

        assertThat(adapter.isShuttingDown()).isTrue();
        worker.join(2000);
        assertThat(worker.isAlive()).isFalse();
        verify(triggerRequestQueue, times(1)).reapNonTerminal(anyString());
    }
}
