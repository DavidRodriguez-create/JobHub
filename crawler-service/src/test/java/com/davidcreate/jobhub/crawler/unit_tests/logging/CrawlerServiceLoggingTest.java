package com.davidcreate.jobhub.crawler.unit_tests.logging;

import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import com.davidcreate.jobhub.crawler.domain.model.PullResult;
import com.davidcreate.jobhub.crawler.domain.model.PullTarget;
import com.davidcreate.jobhub.crawler.domain.port.out.CrawlProgressRecorder;
import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.crawler.domain.port.out.JobSourceClient;
import com.davidcreate.jobhub.crawler.domain.port.out.PullTargetRepository;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownFlag;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import com.davidcreate.jobhub.crawler.domain.service.CrawlerService;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Story #513 (ADR 0029): log-format assertions for {@code CrawlerService}. Same technique as
 * {@code RequestLoggingFilterTest} -- attach a plain {@link java.util.logging.Handler} to the
 * JBoss/JUL category logger (they share the same underlying LogManager instance in this
 * codebase's jboss-logmanager setup) and assert on formatted messages, no prod seam added.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CrawlerService logging (ADR 0029, story #513)")
class CrawlerServiceLoggingTest {

    private static final Logger CRAWLER_SERVICE_LOG = Logger.getLogger(CrawlerService.class.getName());

    @Mock
    PullTargetRepository pullTargetRepository;
    @Mock
    JobPostRepository jobPostRepository;
    @Mock
    Instance<JobSourceClient> clients;
    @Mock
    JobSourceClient mockClient;
    @Mock
    TriggerRequestQueue triggerRequestQueue;
    @Mock
    CrawlProgressRecorder progressRecorder;

    CrawlerService crawlerService;
    private CapturingHandler handler;
    private PullTarget sampleTarget;

    @BeforeEach
    void setUp() throws Exception {
        crawlerService = new CrawlerService(pullTargetRepository, jobPostRepository, clients, triggerRequestQueue,
                progressRecorder);
        setField("cooldownMinutes", 15);
        setField("cooldownRateLimitHours", 1);
        setField("cooldownUnavailableMinutes", 30);
        setField("cooldownNotFoundDays", 1);
        setField("minNewPosts", 100);
        setField("maxTargetsPerRun", 200);

        sampleTarget = PullTarget.builder()
                .id(UUID.randomUUID())
                .sourceType("greenhouse")
                .companyName("TestCo")
                .token("testco")
                .build();

        handler = new CapturingHandler();
        CRAWLER_SERVICE_LOG.addHandler(handler);

        lenient().when(clients.stream()).thenAnswer(inv -> Stream.of(mockClient));
        lenient().when(mockClient.supports("greenhouse")).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        CRAWLER_SERVICE_LOG.removeHandler(handler);
        // ShutdownFlag is a plain static (deliberately, so it stays readable without CDI --
        // ADR 0032, story #398, D1, 4th pass); reset it via reflection so a test that raises
        // it never leaks into a sibling test class sharing this JVM fork. No production reset
        // method exists on purpose: real shutdown is never un-raised.
        Field flagField = ShutdownFlag.class.getDeclaredField("shuttingDown");
        flagField.setAccessible(true);
        flagField.set(null, false);
    }

    @Test
    @DisplayName("TC-513-B9: successful target logs exactly one "
            + "'Crawled <Company> (<sourceType>): N found, M new' INFO line")
    void successfulTargetLogsCombinedLine() {
        when(pullTargetRepository.findAndLockById(sampleTarget.getId())).thenReturn(Optional.of(sampleTarget));
        List<JobPost> twentyPosts = buildPostsWithUniqueUrls(20);
        when(mockClient.crawl(any())).thenReturn(PullResult.success(twentyPosts));

        int[] callCount = {0};
        when(jobPostRepository.findByContentHash(any())).thenAnswer(inv -> {
            callCount[0]++;
            if (callCount[0] <= 15) {
                return Optional.of(buildJobWithHash("existing-" + callCount[0]));
            }
            return Optional.empty();
        });
        when(jobPostRepository.findByUrl(any())).thenReturn(Optional.empty());

        crawlerService.crawl(sampleTarget.getId());

        List<LogRecord> crawledLines = matching(handler.infos(), "Crawled ");
        assertThat(crawledLines).hasSize(1);
        assertThat(crawledLines.get(0).getMessage()).isEqualTo("Crawled TestCo (greenhouse): 20 found, 5 new");
    }

    @Test
    @DisplayName("TC-513-B10: failed target -- no 'Crawled ...' line, WARN unaffected, "
            + "'Crawl progress:' line still fires with the right counters")
    void failedTargetSkipsCombinedLineButProgressStillFires() {
        UUID triggerRequestId = UUID.randomUUID();
        when(pullTargetRepository.findNextAvailableAndLock())
                .thenReturn(Optional.of(sampleTarget))
                .thenReturn(Optional.empty());
        when(mockClient.crawl(any())).thenReturn(PullResult.failure("upstream error", 503));

        crawlerService.crawlBatch(10, triggerRequestId);

        assertThat(matching(handler.records, "Crawled ")).isEmpty();

        List<LogRecord> warnings = handler.warnings();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).getMessage()).isEqualTo("Crawl failed for TestCo: upstream error (HTTP 503)");

        List<LogRecord> progressLines = matching(handler.infos(), "Crawl progress: ");
        assertThat(progressLines).hasSize(1);
        assertThat(progressLines.get(0).getMessage())
                .isEqualTo("Crawl progress: 1 targets visited, 0 new posts so far (target 10)");
    }

    @Test
    @DisplayName("TC-513-B11: 'Crawl progress:' line uses exactly the persisted counters, per step")
    void progressLineUsesPersistedCountersPerStep() throws Exception {
        UUID triggerRequestId = UUID.randomUUID();
        setField("minNewPosts", 100);
        when(pullTargetRepository.findNextAvailableAndLock())
                .thenReturn(Optional.of(sampleTarget))
                .thenReturn(Optional.of(sampleTarget))
                .thenReturn(Optional.of(sampleTarget))
                .thenReturn(Optional.empty());
        when(mockClient.crawl(any()))
                .thenReturn(PullResult.success(buildPostsWithUniqueUrls(20)))
                .thenReturn(PullResult.success(buildPostsWithUniqueUrls(15)))
                .thenReturn(PullResult.success(buildPostsWithUniqueUrls(12)));
        when(jobPostRepository.findByContentHash(any())).thenReturn(Optional.empty());
        when(jobPostRepository.findByUrl(any())).thenReturn(Optional.empty());

        crawlerService.crawlBatch(100, triggerRequestId);

        List<LogRecord> progressLines = matching(handler.infos(), "Crawl progress: ");
        assertThat(progressLines).hasSize(3);
        assertThat(progressLines.get(2).getMessage())
                .isEqualTo("Crawl progress: 3 targets visited, 47 new posts so far (target 100)");
    }

    @Test
    @DisplayName("story #398, D1, 4th pass: work abandoned once shutdown is up does not propagate "
            + "an exception out of the batch, and logs a single quiet INFO line with no stack trace")
    void abandonedWorkDuringShutdownDoesNotEscapeAndLogsQuietly() {
        ShutdownSignal shutdownSignal = mock(ShutdownSignal.class);
        // false at the loop's own item-boundary check, so it enters this item -- the exception
        // handler's own decision reads the CDI-free ShutdownFlag directly (not this mock),
        // exactly like production: the injected proxy cannot be trusted at that point.
        when(shutdownSignal.isShuttingDown()).thenReturn(false);
        // Stands in for a drain-timeout interrupt landing mid-HTTP-call/mid-DB-write: the
        // exception's exact type does not matter, only that it surfaces once shutdown is up.
        when(pullTargetRepository.findNextAvailableAndLock())
                .thenThrow(new RuntimeException("interrupted mid-fetch"));
        ShutdownFlag.raise();

        assertThatCode(() -> crawlerService.crawlBatch(10, null, shutdownSignal))
                .doesNotThrowAnyException();

        List<LogRecord> abandonedLines = matching(handler.infos(), "Crawl abandoned during shutdown");
        assertThat(abandonedLines).hasSize(1);
        assertThat(abandonedLines.get(0).getThrown()).isNull();
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static List<LogRecord> matching(List<LogRecord> records, String prefix) {
        return records.stream()
                .filter(r -> r.getMessage() != null && r.getMessage().startsWith(prefix))
                .toList();
    }

    private List<JobPost> buildPostsWithUniqueUrls(int count) {
        List<JobPost> posts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            posts.add(JobPost.builder()
                    .id(UUID.randomUUID())
                    .targetId(sampleTarget.getId())
                    .title("Job " + i)
                    .url("https://example.com/job/" + UUID.randomUUID())
                    .contentHash("hash-" + UUID.randomUUID())
                    .city("Madrid")
                    .country("Spain")
                    .build());
        }
        return posts;
    }

    private JobPost buildJobWithHash(String hash) {
        return JobPost.builder()
                .id(UUID.randomUUID())
                .targetId(sampleTarget.getId())
                .title("Engineer")
                .url("https://example.com/job/" + UUID.randomUUID())
                .contentHash(hash)
                .city("Madrid")
                .country("Spain")
                .build();
    }

    private void setField(String name, int value) throws Exception {
        Field f = CrawlerService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(crawlerService, value);
    }

    private static class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

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

        List<LogRecord> infos() {
            return records.stream()
                    .filter(r -> r.getLevel().intValue() == Level.INFO.intValue())
                    .toList();
        }

        List<LogRecord> warnings() {
            // org.jboss.logging maps WARN to its own JDKLevel("WARN", 900) instance rather than
            // java.util.logging.Level.WARNING, so compare by severity, not by identity/name.
            return records.stream()
                    .filter(r -> r.getLevel().intValue() == Level.WARNING.intValue())
                    .toList();
        }
    }
}
