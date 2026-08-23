package com.davidcreate.jobhub.crawler.unit_tests.adapter.in.scheduler;

import com.davidcreate.jobhub.crawler.adapter.in.scheduler.TriggerRequestReaper;
import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.model.TriggerRequest;
import com.davidcreate.jobhub.crawler.domain.model.TriggerStatus;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Story #398 (ADR 0032, D2): the live sweep half of {@code TriggerRequestReaper}. The
 * startup half ({@code reapNonTerminal}) is a single bulk repository call with no branching
 * logic worth a unit test in isolation; it is covered end to end by
 * {@code TriggerRequestReaperStartupComponentTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TriggerRequestReaper Unit Tests")
class TriggerRequestReaperTest {

    @Mock
    TriggerRequestQueue triggerRequestQueue;

    @Mock
    ShutdownSignal shutdownSignal;

    TriggerRequestReaper reaper;

    private static final Duration STALE_AFTER = Duration.ofHours(2);

    @BeforeEach
    void setUp() throws Exception {
        reaper = new TriggerRequestReaper(triggerRequestQueue, shutdownSignal);
        Field field = TriggerRequestReaper.class.getDeclaredField("staleAfter");
        field.setAccessible(true);
        field.set(reaper, STALE_AFTER);
    }

    private TriggerRequest running(UUID id, OffsetDateTime startedAt) {
        return TriggerRequest.builder()
                .id(id)
                .kind(TriggerKind.CRAWL)
                .status(TriggerStatus.RUNNING)
                .startedAt(startedAt)
                .build();
    }

    @Test
    @DisplayName("C7: a running row under the stale-after threshold is left untouched")
    void runningRowUnderStaleAfterIsUntouched() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID id = UUID.randomUUID();
        when(triggerRequestQueue.findRunning()).thenReturn(List.of(running(id, now.minusHours(1))));

        reaper.sweepStale(now);

        verify(triggerRequestQueue, never()).markInterrupted(any(), any());
    }

    @Test
    @DisplayName("C15: a row already cancelled via the existing flow is never touched by the sweep")
    void cancelledRowIsSkippedByTheSweep() {
        OffsetDateTime now = OffsetDateTime.now();
        // A cancelled row is terminal: it is never returned by findRunning() (status != running),
        // so the sweep has nothing to interrupt -- it keeps its own outcome = cancelled.
        when(triggerRequestQueue.findRunning()).thenReturn(List.of());

        reaper.sweepStale(now);

        verify(triggerRequestQueue, never()).markInterrupted(any(), any());
    }

    @Test
    @DisplayName("sweep() is gated on the shutdown signal, like the other scheduled entry points")
    void sweepDoesNothingWhileShutdownIsInProgress() {
        when(shutdownSignal.isShuttingDown()).thenReturn(true);

        reaper.sweep();

        verify(triggerRequestQueue, never()).findRunning();
        verify(triggerRequestQueue, never()).markInterrupted(any(), any());
    }
}
