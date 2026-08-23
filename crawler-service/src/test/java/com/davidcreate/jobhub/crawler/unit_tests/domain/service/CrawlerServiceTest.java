package com.davidcreate.jobhub.crawler.unit_tests.domain.service;

import com.davidcreate.jobhub.crawler.domain.exception.ConflictException;
import com.davidcreate.jobhub.crawler.domain.exception.ValidationException;
import com.davidcreate.jobhub.crawler.domain.model.CrawlOutcome;
import com.davidcreate.jobhub.crawler.domain.model.CrawlProgress;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import com.davidcreate.jobhub.crawler.domain.model.PullResult;
import com.davidcreate.jobhub.crawler.domain.model.PullTarget;
import com.davidcreate.jobhub.crawler.domain.model.PullTargetStatus;
import com.davidcreate.jobhub.crawler.domain.model.CrawlBatchResult;
import com.davidcreate.jobhub.crawler.domain.model.TriggerOutcome;
import com.davidcreate.jobhub.crawler.domain.port.out.CrawlProgressRecorder;
import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.crawler.domain.port.out.JobSourceClient;
import com.davidcreate.jobhub.crawler.domain.port.out.PullTargetRepository;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import com.davidcreate.jobhub.crawler.domain.service.CrawlerService;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.temporal.ChronoUnit;

@ExtendWith(MockitoExtension.class)
@DisplayName("CrawlerService Unit Tests")
class CrawlerServiceTest {

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
    }

    private void setField(String name, int value) throws Exception {
        Field f = CrawlerService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(crawlerService, value);
    }

    @Nested
    @DisplayName("crawlBatch(int)")
    class CrawlBatch {

        @Test
        @DisplayName("throws ValidationException when limit is 0")
        void throwsOnZeroLimit() {
            assertThatThrownBy(() -> crawlerService.crawlBatch(0))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("limit must be >= 1");
        }

        @Test
        @DisplayName("CS-U-12: no upper-bound validation -- large value accepted when no targets available")
        void noUpperBoundValidation() {
            when(pullTargetRepository.findNextAvailableAndLock()).thenReturn(Optional.empty());

            CrawlBatchResult result = crawlerService.crawlBatch(1000);

            assertThat(result.getNewPosts()).isZero();
            assertThat(result.getCrawled()).isZero();
        }

        @Test
        @DisplayName("returns crawled=0 when no targets are available")
        void returnsZeroWhenNoTargets() {
            when(pullTargetRepository.findNextAvailableAndLock()).thenReturn(Optional.empty());

            var result = crawlerService.crawlBatch(10);

            assertThat(result.getCrawled()).isZero();
            assertThat(result.getNewPosts()).isZero();
        }

        @Test
        @DisplayName("respects exhaustion stop -- does not crawl beyond available targets")
        void respectsExhaustionStop() {
            lenient().when(clients.stream()).thenReturn(Stream.of(mockClient));
            lenient().when(mockClient.supports("greenhouse")).thenReturn(true);
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.empty());
            when(mockClient.crawl(any())).thenReturn(PullResult.success(List.of()));

            var result = crawlerService.crawlBatch(100);

            assertThat(result.getCrawled()).isEqualTo(1);
        }

        @Test
        @DisplayName("CS-U-01: stops when cumulative new posts reaches the target, with sources still available")
        void stopsWhenNewPostTargetReached() throws Exception {
            setField("maxTargetsPerRun", 200);
            when(clients.stream()).thenAnswer(inv -> Stream.of(mockClient));
            when(mockClient.supports("greenhouse")).thenReturn(true);

            // Source 1 returns 6 new posts, source 2 returns 5 new posts (cumulative 11 >= 10)
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.of(sampleTarget)); // would be 3rd if not stopped

            List<JobPost> sixPosts = buildNewPosts(6);
            List<JobPost> fivePosts = buildNewPosts(5);

            when(mockClient.crawl(any()))
                    .thenReturn(PullResult.success(sixPosts))
                    .thenReturn(PullResult.success(fivePosts));

            // All new (not stored)
            when(jobPostRepository.findByContentHash(any())).thenReturn(Optional.empty());
            when(jobPostRepository.findByUrl(any())).thenReturn(Optional.empty());

            CrawlBatchResult result = crawlerService.crawlBatch(10, null);

            assertThat(result.getNewPosts()).isEqualTo(11);
            assertThat(result.getCrawled()).isEqualTo(2);
            assertThat(result.isCancelled()).isFalse();
            // Third call to findNextAvailableAndLock must never happen
            verify(pullTargetRepository, times(2)).findNextAvailableAndLock();
        }

        @Test
        @DisplayName("CS-U-02: stops on source exhaustion before reaching the new-post target")
        void stopsOnSourceExhaustion() throws Exception {
            when(clients.stream()).thenAnswer(inv -> Stream.of(mockClient));
            when(mockClient.supports("greenhouse")).thenReturn(true);

            // Two targets available, then empty
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.empty());

            List<JobPost> fifteenPosts = buildNewPosts(15);
            when(mockClient.crawl(any())).thenReturn(PullResult.success(fifteenPosts));
            when(jobPostRepository.findByContentHash(any())).thenReturn(Optional.empty());
            when(jobPostRepository.findByUrl(any())).thenReturn(Optional.empty());

            // minNewPosts = 100, but only 30 new posts produced total
            CrawlBatchResult result = crawlerService.crawlBatch(100, null);

            assertThat(result.getNewPosts()).isEqualTo(30);
            assertThat(result.getCrawled()).isEqualTo(2);
            assertThat(result.isCancelled()).isFalse();
            // findNextAvailableAndLock called 3 times (2 successes + 1 empty)
            verify(pullTargetRepository, times(3)).findNextAvailableAndLock();
        }

        @Test
        @DisplayName("CS-U-03: stops when max-targets-per-run safety cap is hit")
        void stopsAtSafetyCapBeforeNewPostTarget() throws Exception {
            setField("maxTargetsPerRun", 3);
            when(clients.stream()).thenAnswer(inv -> Stream.of(mockClient));
            when(mockClient.supports("greenhouse")).thenReturn(true);

            // 50 targets available but cap is 3
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget));

            List<JobPost> onePost = buildNewPosts(1);
            when(mockClient.crawl(any())).thenReturn(PullResult.success(onePost));
            when(jobPostRepository.findByContentHash(any())).thenReturn(Optional.empty());
            when(jobPostRepository.findByUrl(any())).thenReturn(Optional.empty());

            CrawlBatchResult result = crawlerService.crawlBatch(100, null);

            assertThat(result.getCrawled()).isEqualTo(3);
            assertThat(result.getNewPosts()).isEqualTo(3);
            assertThat(result.isCancelled()).isFalse();
            // Cap check fires before calling crawlNext for the 4th time
            verify(pullTargetRepository, times(3)).findNextAvailableAndLock();
        }

        @Test
        @DisplayName("CS-U-04: cancellation beats safety cap -- cancelled before any crawl when cancel fires first")
        void cancellationBeatsSafetyCap() throws Exception {
            setField("maxTargetsPerRun", 2);
            UUID id = UUID.randomUUID();
            // Cancel on the very first check (before any target)
            when(triggerRequestQueue.isCancelRequested(id)).thenReturn(true);

            CrawlBatchResult result = crawlerService.crawlBatch(100, id);

            assertThat(result.getCrawled()).isZero();
            assertThat(result.getNewPosts()).isZero();
            assertThat(result.isCancelled()).isTrue();
            verify(pullTargetRepository, never()).findNextAvailableAndLock();
        }

        @Test
        @DisplayName("CS-U-05: already-stored postings contribute 0 to the new-post count")
        void alreadyStoredPostingsContributeZero() {
            when(clients.stream()).thenAnswer(inv -> Stream.of(mockClient));
            when(mockClient.supports("greenhouse")).thenReturn(true);

            // One target produces 20 posts but 15 are already stored
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.empty());

            List<JobPost> posts = buildNewPostsWithUniqueUrls(20);
            when(mockClient.crawl(any())).thenReturn(PullResult.success(posts));

            // 15 exist by content hash, 5 are new
            int[] callCount = {0};
            when(jobPostRepository.findByContentHash(any())).thenAnswer(inv -> {
                callCount[0]++;
                if (callCount[0] <= 15) {
                    // Return a dummy existing post
                    return Optional.of(buildJobWithHash("existing-" + callCount[0]));
                }
                return Optional.empty();
            });
            when(jobPostRepository.findByUrl(any())).thenReturn(Optional.empty());

            CrawlBatchResult result = crawlerService.crawlBatch(5, null);

            ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
            verify(jobPostRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(5);
            assertThat(result.getNewPosts()).isEqualTo(5);
        }

        @Test
        @DisplayName("CS-U-06: intra-source URL duplicate counted at most once")
        void intraSurlUrlDuplicateCountedOnce() {
            when(clients.stream()).thenAnswer(inv -> Stream.of(mockClient));
            when(mockClient.supports("greenhouse")).thenReturn(true);

            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.empty());

            // postA and postB share the same URL, postC has a unique URL
            UUID targetId = sampleTarget.getId();
            JobPost postA = JobPost.builder()
                    .id(UUID.randomUUID()).targetId(targetId)
                    .title("Dev").url("https://example.com/job/dup")
                    .contentHash("hash-a").city("Madrid").country("Spain").build();
            JobPost postB = JobPost.builder()
                    .id(UUID.randomUUID()).targetId(targetId)
                    .title("Dev").url("https://example.com/job/dup")
                    .contentHash("hash-b").city("Madrid").country("Spain").build();
            JobPost postC = JobPost.builder()
                    .id(UUID.randomUUID()).targetId(targetId)
                    .title("Dev").url("https://example.com/job/unique")
                    .contentHash("hash-c").city("Madrid").country("Spain").build();

            when(mockClient.crawl(any())).thenReturn(PullResult.success(List.of(postA, postB, postC)));
            when(jobPostRepository.findByContentHash(any())).thenReturn(Optional.empty());
            when(jobPostRepository.findByUrl(any())).thenReturn(Optional.empty());

            CrawlBatchResult result = crawlerService.crawlBatch(5, null);

            ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
            verify(jobPostRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(2);
            assertThat(result.getNewPosts()).isEqualTo(2);
        }

        @Test
        @DisplayName("CS-U-07: failed source pull contributes 0 to new-post count but still counts as 1 target visited")
        void failedSourceContributesZeroNewPosts() {
            when(clients.stream()).thenAnswer(inv -> Stream.of(mockClient));
            when(mockClient.supports("greenhouse")).thenReturn(true);

            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.empty());

            when(mockClient.crawl(any())).thenReturn(PullResult.failure("upstream error"));

            CrawlBatchResult result = crawlerService.crawlBatch(10, null);

            verify(jobPostRepository, never()).saveAll(any());
            assertThat(result.getNewPosts()).isZero();
            assertThat(result.getCrawled()).isEqualTo(1);
        }

        @Test
        @DisplayName("CS-U-08: whole-source granularity -- stop check fires only after a complete source step")
        void wholeSourceGranularity() {
            when(clients.stream()).thenAnswer(inv -> Stream.of(mockClient));
            when(mockClient.supports("greenhouse")).thenReturn(true);

            // Source 1 produces 8 new posts (cumulative 8, below target 10)
            // Source 2 produces 5 new posts (cumulative 13, >= 10, loop stops after)
            // A third source is available but must not be crawled
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.of(sampleTarget)); // third, must not be called

            List<JobPost> eightPosts = buildNewPostsWithUniqueUrls(8);
            List<JobPost> fivePosts = buildNewPostsWithUniqueUrls(5);

            when(mockClient.crawl(any()))
                    .thenReturn(PullResult.success(eightPosts))
                    .thenReturn(PullResult.success(fivePosts));

            when(jobPostRepository.findByContentHash(any())).thenReturn(Optional.empty());
            when(jobPostRepository.findByUrl(any())).thenReturn(Optional.empty());

            CrawlBatchResult result = crawlerService.crawlBatch(10, null);

            assertThat(result.getNewPosts()).isEqualTo(13);
            assertThat(result.getCrawled()).isEqualTo(2);
            // Third findNextAvailableAndLock must never be reached
            verify(pullTargetRepository, times(2)).findNextAvailableAndLock();
        }

        @Test
        @DisplayName("CS-U-11: minNewPosts < 1 throws ValidationException")
        void throwsOnNegativeLimit() {
            assertThatThrownBy(() -> crawlerService.crawlBatch(-5))
                    .isInstanceOf(ValidationException.class);

            assertThatThrownBy(() -> crawlerService.crawlBatch(0))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    @DisplayName("crawlBatch(int, UUID) -- cooperative cancellation")
    class CrawlBatchWithTriggerId {

        @BeforeEach
        void stubClient() {
            lenient().when(clients.stream()).thenAnswer(invocation -> Stream.of(mockClient));
            lenient().when(mockClient.supports("greenhouse")).thenReturn(true);
            lenient().when(mockClient.crawl(any())).thenReturn(PullResult.success(List.of()));
        }

        @Test
        @DisplayName("CS-UNIT-01: exits early when cancel_requested is detected after the 2nd target")
        void exitsEarlyOnCancelRequested() {
            UUID triggerRequestId = UUID.randomUUID();
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget), Optional.of(sampleTarget), Optional.of(sampleTarget));
            when(triggerRequestQueue.isCancelRequested(triggerRequestId)).thenReturn(false, false, true);

            CrawlBatchResult result = crawlerService.crawlBatch(10, triggerRequestId);

            verify(mockClient, times(2)).crawl(any());
            assertThat(result.getCrawled()).isEqualTo(2);
            assertThat(result.getNewPosts()).isEqualTo(0);
            assertThat(result.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("CS-U-09: cancellation stops the run early and newPosts reflects partial progress")
        void cancellationStopsEarlyWithPartialProgress() throws Exception {
            UUID id = UUID.randomUUID();
            // isCancelRequested: false, false, true (detected before the 3rd source)
            when(triggerRequestQueue.isCancelRequested(id)).thenReturn(false, false, true);

            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.of(sampleTarget));

            List<JobPost> tenPosts = buildNewPostsWithUniqueUrls(10);
            when(mockClient.crawl(any())).thenReturn(PullResult.success(tenPosts));
            when(jobPostRepository.findByContentHash(any())).thenReturn(Optional.empty());
            when(jobPostRepository.findByUrl(any())).thenReturn(Optional.empty());

            CrawlBatchResult result = crawlerService.crawlBatch(100, id);

            assertThat(result.getCrawled()).isEqualTo(2);
            assertThat(result.getNewPosts()).isEqualTo(20);
            assertThat(result.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("CS-U-10: no cancellation check when triggerRequestId is null (cron path)")
        void noCancellationCheckWhenTriggerIdNull() {
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.empty());

            CrawlBatchResult result = crawlerService.crawlBatch(100, null);

            assertThat(result.getCrawled()).isEqualTo(1);
            assertThat(result.isCancelled()).isFalse();
            assertThat(result.getNewPosts()).isEqualTo(0);
            verify(triggerRequestQueue, never()).isCancelRequested(any());
        }

        @Test
        @DisplayName("completes normally and reports cancelled=false when never cancelled")
        void completesNormallyWhenNotCancelled() {
            UUID triggerRequestId = UUID.randomUUID();
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget), Optional.of(sampleTarget), Optional.empty());
            when(triggerRequestQueue.isCancelRequested(triggerRequestId)).thenReturn(false);

            CrawlBatchResult result = crawlerService.crawlBatch(10, triggerRequestId);

            assertThat(result.getCrawled()).isEqualTo(2);
            assertThat(result.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("does not check cancellation when triggerRequestId is null (cron path)")
        void noCancellationCheckWhenTriggerIdNullLegacy() {
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget), Optional.empty());

            CrawlBatchResult result = crawlerService.crawlBatch(10, null);

            assertThat(result.getCrawled()).isEqualTo(1);
            assertThat(result.isCancelled()).isFalse();
            verify(triggerRequestQueue, never()).isCancelRequested(any());
        }
    }

    @Nested
    @DisplayName("crawlBatch(int, UUID, ShutdownSignal) -- shutdown safety and no-targets outcome (story #398)")
    class ShutdownAndOutcome {

        @BeforeEach
        void stubClient() {
            lenient().when(clients.stream()).thenAnswer(inv -> Stream.of(mockClient));
            lenient().when(mockClient.supports("greenhouse")).thenReturn(true);
            lenient().when(mockClient.crawl(any())).thenReturn(PullResult.success(List.of()));
        }

        @Test
        @DisplayName("C1: shutdown detected after the 1st target stops the loop before a 2nd item starts")
        void shutdownStopsLoopAtItemBoundary() {
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget), Optional.of(sampleTarget), Optional.of(sampleTarget));
            ShutdownSignal shutdownSignal = mock(ShutdownSignal.class);
            when(shutdownSignal.isShuttingDown()).thenReturn(false, true);

            CrawlBatchResult result = crawlerService.crawlBatch(100, null, shutdownSignal);

            verify(mockClient, times(1)).crawl(any());
            assertThat(result.getCrawled()).isEqualTo(1);
        }

        @Test
        @DisplayName("C9: zero eligible targets completes with outcome=no_targets, never a bare 'crawled 0 targets'")
        void zeroEligibleTargetsReportsNoTargetsOutcome() {
            when(pullTargetRepository.findNextAvailableAndLock()).thenReturn(Optional.empty());

            CrawlBatchResult result = crawlerService.crawlBatch(10, null);

            assertThat(result.getCrawled()).isZero();
            assertThat(result.getNewPosts()).isZero();
            assertThat(result.getOutcome()).isEqualTo(TriggerOutcome.NO_TARGETS);
        }

        @Test
        @DisplayName("a batch that visits at least one target reports outcome=completed")
        void nonEmptyBatchReportsCompletedOutcome() {
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget), Optional.empty());

            CrawlBatchResult result = crawlerService.crawlBatch(10, null);

            assertThat(result.getOutcome()).isEqualTo(TriggerOutcome.COMPLETED);
        }
    }

    @Nested
    @DisplayName("progress reporting (ADR 0029, story #513)")
    class ProgressReporting {

        @BeforeEach
        void stubClient() {
            lenient().when(clients.stream()).thenAnswer(inv -> Stream.of(mockClient));
            lenient().when(mockClient.supports("greenhouse")).thenReturn(true);
        }

        @Test
        @DisplayName("TC-513-B1: markCurrentTarget is called before the target's crawl, with its company/sourceType")
        void marksCurrentTargetBeforeCrawl() {
            UUID triggerRequestId = UUID.randomUUID();
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.empty());
            when(mockClient.crawl(any())).thenReturn(PullResult.success(List.of()));

            crawlerService.crawlBatch(10, triggerRequestId);

            InOrder inOrder = inOrder(progressRecorder, mockClient);
            inOrder.verify(progressRecorder).markCurrentTarget(triggerRequestId, "TestCo", "greenhouse");
            inOrder.verify(mockClient).crawl(any());
        }

        @Test
        @DisplayName("TC-513-B2: recordTargetCompleted carries the running totals and the just-finished "
                + "target's own found/new pair, per step")
        void recordsRunningTotalsPerStep() {
            UUID triggerRequestId = UUID.randomUUID();
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.empty());

            List<JobPost> eightPosts = buildNewPostsWithUniqueUrls(8);
            List<JobPost> sevenPosts = buildNewPostsWithUniqueUrls(7);
            when(mockClient.crawl(any()))
                    .thenReturn(PullResult.success(eightPosts))
                    .thenReturn(PullResult.success(sevenPosts));

            // Calls 1,2 (step 1) and 9,10 (step 2) are "already stored" -> step1: 8 found/6 new,
            // step2: 7 found/5 new, cumulative newPosts 6 then 11.
            int[] callCount = {0};
            java.util.Set<Integer> existingCalls = java.util.Set.of(1, 2, 9, 10);
            when(jobPostRepository.findByContentHash(any())).thenAnswer(inv -> {
                callCount[0]++;
                if (existingCalls.contains(callCount[0])) {
                    return Optional.of(buildJobWithHash("existing-" + callCount[0]));
                }
                return Optional.empty();
            });
            when(jobPostRepository.findByUrl(any())).thenReturn(Optional.empty());

            crawlerService.crawlBatch(100, triggerRequestId);

            ArgumentCaptor<CrawlProgress> captor = ArgumentCaptor.forClass(CrawlProgress.class);
            verify(progressRecorder, times(2)).recordTargetCompleted(eq(triggerRequestId), captor.capture());
            List<CrawlProgress> calls = captor.getAllValues();

            assertThat(calls.get(0).getTargetsVisited()).isEqualTo(1);
            assertThat(calls.get(0).getNewPosts()).isEqualTo(6);
            assertThat(calls.get(0).getLastCompanyName()).isEqualTo("TestCo");
            assertThat(calls.get(0).getLastSourceType()).isEqualTo("greenhouse");
            assertThat(calls.get(0).getLastFoundPosts()).isEqualTo(8);
            assertThat(calls.get(0).getLastNewPosts()).isEqualTo(6);

            assertThat(calls.get(1).getTargetsVisited()).isEqualTo(2);
            assertThat(calls.get(1).getNewPosts()).isEqualTo(11);
            assertThat(calls.get(1).getLastFoundPosts()).isEqualTo(7);
            assertThat(calls.get(1).getLastNewPosts()).isEqualTo(5);
        }

        @Test
        @DisplayName("TC-513-B3: a failing target still calls recordTargetCompleted with a zero "
                + "contribution, and the batch continues")
        void failingTargetRecordsZeroContribution() {
            UUID triggerRequestId = UUID.randomUUID();
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.empty());
            when(mockClient.crawl(any()))
                    .thenReturn(PullResult.failure("upstream error", 503))
                    .thenReturn(PullResult.success(buildNewPostsWithUniqueUrls(3)));
            when(jobPostRepository.findByContentHash(any())).thenReturn(Optional.empty());
            when(jobPostRepository.findByUrl(any())).thenReturn(Optional.empty());

            crawlerService.crawlBatch(10, triggerRequestId);

            ArgumentCaptor<CrawlProgress> captor = ArgumentCaptor.forClass(CrawlProgress.class);
            verify(progressRecorder, times(2)).recordTargetCompleted(eq(triggerRequestId), captor.capture());
            List<CrawlProgress> calls = captor.getAllValues();

            assertThat(calls.get(0).getTargetsVisited()).isEqualTo(1);
            assertThat(calls.get(0).getNewPosts()).isEqualTo(0);
            assertThat(calls.get(0).getLastFoundPosts()).isEqualTo(0);
            assertThat(calls.get(0).getLastNewPosts()).isEqualTo(0);

            assertThat(calls.get(1).getTargetsVisited()).isEqualTo(2);
            assertThat(calls.get(1).getNewPosts()).isEqualTo(3);
        }

        @Test
        @DisplayName("TC-513-B4a: clearCurrentTarget fires exactly once on source exhaustion")
        void clearsCurrentTargetOnSourceExhaustion() {
            UUID triggerRequestId = UUID.randomUUID();
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.empty());
            when(mockClient.crawl(any())).thenReturn(PullResult.success(List.of()));

            crawlerService.crawlBatch(100, triggerRequestId);

            verify(progressRecorder, times(1)).clearCurrentTarget(triggerRequestId);
        }

        @Test
        @DisplayName("TC-513-B4b: clearCurrentTarget fires exactly once when the safety cap is hit")
        void clearsCurrentTargetOnSafetyCap() throws Exception {
            UUID triggerRequestId = UUID.randomUUID();
            setField("maxTargetsPerRun", 2);
            when(pullTargetRepository.findNextAvailableAndLock()).thenReturn(Optional.of(sampleTarget));
            when(mockClient.crawl(any())).thenReturn(PullResult.success(List.of()));

            crawlerService.crawlBatch(100, triggerRequestId);

            verify(progressRecorder, times(1)).clearCurrentTarget(triggerRequestId);
        }

        @Test
        @DisplayName("TC-513-B4c: clearCurrentTarget fires exactly once when the new-post target is reached")
        void clearsCurrentTargetOnTargetReached() {
            UUID triggerRequestId = UUID.randomUUID();
            when(pullTargetRepository.findNextAvailableAndLock()).thenReturn(Optional.of(sampleTarget));
            when(mockClient.crawl(any())).thenReturn(PullResult.success(buildNewPostsWithUniqueUrls(10)));
            when(jobPostRepository.findByContentHash(any())).thenReturn(Optional.empty());
            when(jobPostRepository.findByUrl(any())).thenReturn(Optional.empty());

            crawlerService.crawlBatch(5, triggerRequestId);

            verify(progressRecorder, times(1)).clearCurrentTarget(triggerRequestId);
        }

        @Test
        @DisplayName("TC-513-B4d: clearCurrentTarget fires exactly once on cancellation")
        void clearsCurrentTargetOnCancellation() {
            UUID triggerRequestId = UUID.randomUUID();
            when(triggerRequestQueue.isCancelRequested(triggerRequestId)).thenReturn(true);

            crawlerService.crawlBatch(100, triggerRequestId);

            verify(progressRecorder, times(1)).clearCurrentTarget(triggerRequestId);
        }

        @Test
        @DisplayName("TC-513-B5: null triggerRequestId (scheduler path) -- zero interactions with the recorder")
        void nullTriggerRequestIdMeansNoRecorderInteractions() {
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.empty());
            when(mockClient.crawl(any())).thenReturn(PullResult.success(List.of()));

            crawlerService.crawlBatch(10, null);

            verifyNoInteractions(progressRecorder);
        }

        @Test
        @DisplayName("TC-513-B6a: markCurrentTarget throwing does not fail the batch")
        void markCurrentTargetThrowingDoesNotFailBatch() {
            UUID triggerRequestId = UUID.randomUUID();
            doThrow(new RuntimeException("db down")).when(progressRecorder)
                    .markCurrentTarget(any(), any(), any());
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.empty());
            List<JobPost> onePost = buildNewPostsWithUniqueUrls(1);
            when(mockClient.crawl(any())).thenReturn(PullResult.success(onePost));
            when(jobPostRepository.findByContentHash(any())).thenReturn(Optional.empty());
            when(jobPostRepository.findByUrl(any())).thenReturn(Optional.empty());

            CrawlBatchResult result = crawlerService.crawlBatch(10, triggerRequestId);

            assertThat(result.getCrawled()).isEqualTo(1);
            assertThat(result.getNewPosts()).isEqualTo(1);
            verify(jobPostRepository).saveAll(any());
        }

        @Test
        @DisplayName("TC-513-B6b: recordTargetCompleted throwing does not fail the batch")
        void recordTargetCompletedThrowingDoesNotFailBatch() {
            UUID triggerRequestId = UUID.randomUUID();
            doThrow(new RuntimeException("db down")).when(progressRecorder)
                    .recordTargetCompleted(any(), any());
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.empty());
            List<JobPost> onePost = buildNewPostsWithUniqueUrls(1);
            when(mockClient.crawl(any())).thenReturn(PullResult.success(onePost));
            when(jobPostRepository.findByContentHash(any())).thenReturn(Optional.empty());
            when(jobPostRepository.findByUrl(any())).thenReturn(Optional.empty());

            CrawlBatchResult result = crawlerService.crawlBatch(10, triggerRequestId);

            assertThat(result.getCrawled()).isEqualTo(1);
            assertThat(result.getNewPosts()).isEqualTo(1);
            verify(jobPostRepository).saveAll(any());
        }

        @Test
        @DisplayName("TC-513-B7: crawlNext(UUID) returns a CrawlOutcome populated with "
                + "company/sourceType/foundPosts/newPosts")
        void crawlNextReturnsPopulatedOutcome() {
            UUID triggerRequestId = UUID.randomUUID();
            when(pullTargetRepository.findNextAvailableAndLock()).thenReturn(Optional.of(sampleTarget));
            List<JobPost> twentyPosts = buildNewPostsWithUniqueUrls(20);
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

            Optional<CrawlOutcome> outcome = crawlerService.crawlNext(triggerRequestId);

            assertThat(outcome).isPresent();
            assertThat(outcome.get().getCompanyName()).isEqualTo("TestCo");
            assertThat(outcome.get().getSourceType()).isEqualTo("greenhouse");
            assertThat(outcome.get().getFoundPosts()).isEqualTo(20);
            assertThat(outcome.get().getNewPosts()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("crawl(UUID)")
    class CrawlById {

        @Test
        @DisplayName("throws ConflictException when target is locked or missing")
        void throwsConflictWhenLocked() {
            UUID id = UUID.randomUUID();
            when(pullTargetRepository.findAndLockById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> crawlerService.crawl(id))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("calls crawl on the matching client when target is available")
        void invokesClientWhenAvailable() {
            when(clients.stream()).thenReturn(Stream.of(mockClient));
            when(mockClient.supports("greenhouse")).thenReturn(true);
            UUID id = sampleTarget.getId();
            when(pullTargetRepository.findAndLockById(id)).thenReturn(Optional.of(sampleTarget));
            when(mockClient.crawl(any())).thenReturn(PullResult.success(List.of()));

            crawlerService.crawl(id);

            verify(mockClient).crawl(sampleTarget);
        }
    }

    @Nested
    @DisplayName("resolveCooldown via crawl failure")
    class ResolveCooldown {

        @BeforeEach
        void stubClient() {
            when(clients.stream()).thenReturn(Stream.of(mockClient));
            when(mockClient.supports("greenhouse")).thenReturn(true);
        }

        @Test
        @DisplayName("uses rate-limit cooldown for HTTP 429")
        void rateLimitCooldown() {
            PullTarget target = buildTarget();
            when(pullTargetRepository.findAndLockById(target.getId())).thenReturn(Optional.of(target));
            when(mockClient.crawl(any())).thenReturn(PullResult.failure("rate limited", 429));

            crawlerService.crawl(target.getId());

            ArgumentCaptor<PullTarget> captor = ArgumentCaptor.forClass(PullTarget.class);
            verify(pullTargetRepository).save(captor.capture());
            assertThat(captor.getValue().getNextPullAfter())
                    .isCloseTo(OffsetDateTime.now().plusHours(1), within(5, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("uses unavailable cooldown for HTTP 503")
        void unavailableCooldown() {
            PullTarget target = buildTarget();
            when(pullTargetRepository.findAndLockById(target.getId())).thenReturn(Optional.of(target));
            when(mockClient.crawl(any())).thenReturn(PullResult.failure("unavailable", 503));

            crawlerService.crawl(target.getId());

            ArgumentCaptor<PullTarget> captor = ArgumentCaptor.forClass(PullTarget.class);
            verify(pullTargetRepository).save(captor.capture());
            assertThat(captor.getValue().getNextPullAfter())
                    .isCloseTo(OffsetDateTime.now().plusMinutes(30), within(5, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("uses long cooldown for HTTP 404")
        void notFoundCooldown() {
            PullTarget target = buildTarget();
            when(pullTargetRepository.findAndLockById(target.getId())).thenReturn(Optional.of(target));
            when(mockClient.crawl(any())).thenReturn(PullResult.failure("not found", 404));

            crawlerService.crawl(target.getId());

            ArgumentCaptor<PullTarget> captor = ArgumentCaptor.forClass(PullTarget.class);
            verify(pullTargetRepository).save(captor.capture());
            assertThat(captor.getValue().getNextPullAfter())
                    .isCloseTo(OffsetDateTime.now().plusDays(1), within(5, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("uses default cooldown when no HTTP status")
        void defaultCooldown() {
            PullTarget target = buildTarget();
            when(pullTargetRepository.findAndLockById(target.getId())).thenReturn(Optional.of(target));
            when(mockClient.crawl(any())).thenReturn(PullResult.failure("unknown error"));

            crawlerService.crawl(target.getId());

            ArgumentCaptor<PullTarget> captor = ArgumentCaptor.forClass(PullTarget.class);
            verify(pullTargetRepository).save(captor.capture());
            assertThat(captor.getValue().getNextPullAfter())
                    .isCloseTo(OffsetDateTime.now().plusMinutes(15), within(5, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("target transitions to DISABLED_TRANSIENT after 5 consecutive failures")
        void disabledTransientAfterFiveFailures() {
            PullTarget target = PullTarget.builder()
                    .id(UUID.randomUUID())
                    .sourceType("greenhouse")
                    .companyName("TestCo")
                    .token("testco")
                    .consecutiveFailures((short) 4)
                    .build();
            when(pullTargetRepository.findAndLockById(target.getId())).thenReturn(Optional.of(target));
            when(mockClient.crawl(any())).thenReturn(PullResult.failure("error"));

            crawlerService.crawl(target.getId());

            ArgumentCaptor<PullTarget> captor = ArgumentCaptor.forClass(PullTarget.class);
            verify(pullTargetRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PullTargetStatus.DISABLED_TRANSIENT);
        }

        private PullTarget buildTarget() {
            return PullTarget.builder()
                    .id(UUID.randomUUID())
                    .sourceType("greenhouse")
                    .companyName("TestCo")
                    .token("testco")
                    .build();
        }
    }

    @Nested
    @DisplayName("persistJobs deduplication")
    class PersistJobs {

        @BeforeEach
        void stubClient() {
            when(clients.stream()).thenReturn(Stream.of(mockClient));
            when(mockClient.supports("greenhouse")).thenReturn(true);
        }

        @Test
        @DisplayName("does not saveAll when all jobs already exist by content hash")
        void skipsExistingJobs() {
            JobPost existing = buildJob();
            when(pullTargetRepository.findAndLockById(sampleTarget.getId()))
                    .thenReturn(Optional.of(sampleTarget));
            when(mockClient.crawl(any())).thenReturn(PullResult.success(List.of(existing)));
            when(jobPostRepository.findByContentHash(existing.getContentHash()))
                    .thenReturn(Optional.of(existing));

            crawlerService.crawl(sampleTarget.getId());

            verify(jobPostRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("calls saveAll only for genuinely new jobs")
        void savesOnlyNewJobs() {
            JobPost newJob = buildJob();
            when(pullTargetRepository.findAndLockById(sampleTarget.getId()))
                    .thenReturn(Optional.of(sampleTarget));
            when(mockClient.crawl(any())).thenReturn(PullResult.success(List.of(newJob)));
            when(jobPostRepository.findByContentHash(newJob.getContentHash()))
                    .thenReturn(Optional.empty());

            crawlerService.crawl(sampleTarget.getId());

            verify(jobPostRepository).saveAll(List.of(newJob));
        }

        @Test
        @DisplayName("does not insert when the URL already exists but content changed "
                + "(regression: uq_job_post_url violation aborted the whole batch)")
        void skipsInsertWhenUrlExistsWithChangedContent() {
            JobPost changed = JobPost.builder()
                    .id(UUID.randomUUID())
                    .targetId(sampleTarget.getId())
                    .title("Engineer (updated)")
                    .url("https://example.com/job/1")
                    .contentHash("hash-new")
                    .build();
            JobPost stored = buildJob(); // same URL, old hash "hash-abc"

            when(pullTargetRepository.findAndLockById(sampleTarget.getId()))
                    .thenReturn(Optional.of(sampleTarget));
            when(mockClient.crawl(any())).thenReturn(PullResult.success(List.of(changed)));
            when(jobPostRepository.findByContentHash("hash-new")).thenReturn(Optional.empty());
            when(jobPostRepository.findByUrl("https://example.com/job/1"))
                    .thenReturn(Optional.of(stored));

            crawlerService.crawl(sampleTarget.getId());

            verify(jobPostRepository, never()).saveAll(any());
            verify(jobPostRepository).save(stored); // existing row's last_seen_at bumped instead
        }

        @Test
        @DisplayName("de-dupes the same URL within one pull, inserting it only once")
        void dedupesSameUrlWithinPull() {
            JobPost first = buildJobWithHash("hash-1");
            JobPost second = buildJobWithHash("hash-2"); // same URL, different hash

            when(pullTargetRepository.findAndLockById(sampleTarget.getId()))
                    .thenReturn(Optional.of(sampleTarget));
            when(mockClient.crawl(any())).thenReturn(PullResult.success(List.of(first, second)));
            when(jobPostRepository.findByContentHash(any())).thenReturn(Optional.empty());
            when(jobPostRepository.findByUrl(any())).thenReturn(Optional.empty());

            crawlerService.crawl(sampleTarget.getId());

            verify(jobPostRepository).saveAll(List.of(first));
        }

        private JobPost buildJob() {
            return buildJobWithHash("hash-abc");
        }

        private JobPost buildJobWithHash(String contentHash) {
            return JobPost.builder()
                    .id(UUID.randomUUID())
                    .targetId(sampleTarget.getId())
                    .title("Engineer")
                    .url("https://example.com/job/1")
                    .contentHash(contentHash)
                    .city("Madrid")
                    .country("Spain")
                    .build();
        }
    }

    // Helper builders

    private List<JobPost> buildNewPosts(int count) {
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

    private List<JobPost> buildNewPostsWithUniqueUrls(int count) {
        return buildNewPosts(count);
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
}
