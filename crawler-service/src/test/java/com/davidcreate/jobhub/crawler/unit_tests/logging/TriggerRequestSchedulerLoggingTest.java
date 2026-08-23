package com.davidcreate.jobhub.crawler.unit_tests.logging;

import com.davidcreate.jobhub.crawler.adapter.in.scheduler.EnrichmentBackoffState;
import com.davidcreate.jobhub.crawler.adapter.in.scheduler.TriggerRequestScheduler;
import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.port.in.CrawlUseCase;
import com.davidcreate.jobhub.crawler.domain.port.in.EnrichJobsUseCase;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownFlag;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Story #398 (ADR 0032, D1, 5th pass): {@code TriggerRequestScheduler.isCrawlRunning()} has
 * its own pre-existing loud ERROR catch, separate from the quiet-catch guards added in
 * earlier passes -- it is reached from inside {@code ShutdownFlag#guardScheduledTick}, so
 * without its own gate it can win the race and log loudly (with a stack trace) even though
 * the failure is expected once shutdown is up. Pins both directions: loud while running,
 * quiet once the flag is raised.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TriggerRequestScheduler logging (story #398, D1, 5th pass)")
class TriggerRequestSchedulerLoggingTest {

    private static final Logger SCHEDULER_LOG = Logger.getLogger(TriggerRequestScheduler.class.getName());
    private static final String LOUD_MESSAGE = "Failed to check for a running crawl; skipping enrichment as a precaution";
    private static final String QUIET_PREFIX = "Crawl-running check abandoned during shutdown";

    @Mock
    TriggerRequestQueue triggerRequestQueue;
    @Mock
    CrawlUseCase crawlUseCase;
    @Mock
    EnrichJobsUseCase enrichJobsUseCase;
    @Mock
    EnrichmentBackoffState backoffState;
    @Mock
    ShutdownSignal shutdownSignal;

    TriggerRequestScheduler scheduler;
    private CapturingHandler handler;

    @BeforeEach
    void setUp() {
        scheduler = new TriggerRequestScheduler(triggerRequestQueue, crawlUseCase, enrichJobsUseCase, backoffState,
                shutdownSignal);
        handler = new CapturingHandler();
        SCHEDULER_LOG.addHandler(handler);
    }

    @AfterEach
    void tearDown() throws Exception {
        SCHEDULER_LOG.removeHandler(handler);
        Field flagField = ShutdownFlag.class.getDeclaredField("shuttingDown");
        flagField.setAccessible(true);
        flagField.set(null, false);
    }

    @Test
    @DisplayName("flag down: repository throws -- still logs the loud ERROR with a throwable, and treats a crawl as running")
    void logsLoudErrorAndReturnsSafeValueWhenFlagIsDown() {
        when(triggerRequestQueue.hasRunning(TriggerKind.CRAWL)).thenThrow(new RuntimeException("DB down"));

        scheduler.run();

        // isCrawlRunning() is checked twice per tick (once to gate crawl claiming, once to
        // gate enrichment claiming), so the repository throws -- and this catch logs -- twice.
        List<LogRecord> loud = matching(handler.records(Level.SEVERE), LOUD_MESSAGE);
        assertThat(loud).isNotEmpty();
        assertThat(loud).allSatisfy(r -> assertThat(r.getThrown()).isNotNull());
        assertThat(matching(handler.records(Level.INFO), QUIET_PREFIX)).isEmpty();
        // The safe value (treat as running) means enrichment is skipped: no ENRICHMENT claim.
        org.mockito.Mockito.verify(triggerRequestQueue, org.mockito.Mockito.never()).claimNext(TriggerKind.ENRICHMENT);
    }

    @Test
    @DisplayName("flag up: repository throws -- logs quietly (no throwable attached), no loud ERROR, still treats a crawl as running")
    void logsQuietlyWithNoThrowableWhenFlagIsUp() {
        when(triggerRequestQueue.hasRunning(TriggerKind.CRAWL)).thenThrow(new RuntimeException("Session/EntityManager is closed"));
        ShutdownFlag.raise();

        scheduler.run();

        assertThat(matching(handler.records(Level.SEVERE), LOUD_MESSAGE)).isEmpty();
        List<LogRecord> quiet = matching(handler.records(Level.INFO), QUIET_PREFIX);
        assertThat(quiet).isNotEmpty();
        assertThat(quiet).allSatisfy(r -> assertThat(r.getThrown()).isNull());
        org.mockito.Mockito.verify(triggerRequestQueue, org.mockito.Mockito.never()).claimNext(TriggerKind.ENRICHMENT);
    }

    private static List<LogRecord> matching(List<LogRecord> records, String prefix) {
        return records.stream()
                .filter(r -> r.getMessage() != null && r.getMessage().startsWith(prefix))
                .toList();
    }

    private static class CapturingHandler extends Handler {
        private final List<LogRecord> records = new java.util.ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<LogRecord> records(Level level) {
            return records.stream().filter(r -> r.getLevel().intValue() == level.intValue()).toList();
        }
    }
}
