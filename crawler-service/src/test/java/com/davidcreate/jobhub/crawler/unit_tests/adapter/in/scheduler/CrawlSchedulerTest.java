package com.davidcreate.jobhub.crawler.unit_tests.adapter.in.scheduler;

import com.davidcreate.jobhub.crawler.adapter.in.scheduler.CrawlerScheduler;
import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Story #398 (ADR 0032, N2): {@code CrawlerScheduler} no longer crawls directly -- it
 * records the automatic pass as a real {@code trigger_request} row (origin = scheduled)
 * for {@code TriggerRequestScheduler} to claim/execute, and yields its own tick whenever
 * any crawl run (either origin) is already active.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CrawlerScheduler Unit Tests")
class CrawlSchedulerTest {

    @Mock
    TriggerRequestQueue triggerRequestQueue;

    @Mock
    ShutdownSignal shutdownSignal;

    CrawlerScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CrawlerScheduler(triggerRequestQueue, shutdownSignal);
    }

    @Test
    @DisplayName("C3: shutdown in progress -- run() returns at once with zero repository calls")
    void shutdownInProgressReturnsAtOnceWithZeroRepoCalls() {
        when(shutdownSignal.isShuttingDown()).thenReturn(true);

        scheduler.run();

        verify(triggerRequestQueue, never()).hasActive(any());
        verify(triggerRequestQueue, never()).enqueue(any(), any(), any());
    }

    @Test
    @DisplayName("C11: a crawl run is already active -- the tick skips and no row is claimed/enqueued")
    void skipsTickWhenCrawlAlreadyActive() {
        when(triggerRequestQueue.hasActive(TriggerKind.CRAWL)).thenReturn(true);

        scheduler.run();

        verify(triggerRequestQueue, never()).enqueue(any(), any(), any());
    }
}
