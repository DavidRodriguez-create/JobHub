package com.davidcreate.jobhub.crawler.unit_tests.domain.service;

import com.davidcreate.jobhub.crawler.domain.exception.ConflictException;
import com.davidcreate.jobhub.crawler.domain.exception.ValidationException;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import com.davidcreate.jobhub.crawler.domain.model.PullResult;
import com.davidcreate.jobhub.crawler.domain.model.PullTarget;
import com.davidcreate.jobhub.crawler.domain.model.PullTargetStatus;
import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.crawler.domain.port.out.JobSourceClient;
import com.davidcreate.jobhub.crawler.domain.port.out.PullTargetRepository;
import com.davidcreate.jobhub.crawler.domain.service.CrawlerService;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
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

    CrawlerService crawlerService;

    private PullTarget sampleTarget;

    @BeforeEach
    void setUp() throws Exception {
        crawlerService = new CrawlerService(pullTargetRepository, jobPostRepository, clients);
        setField("cooldownMinutes", 15);
        setField("cooldownRateLimitHours", 1);
        setField("cooldownUnavailableMinutes", 30);
        setField("cooldownNotFoundDays", 1);

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
                    .hasMessageContaining("limit must be between 1 and 50");
        }

        @Test
        @DisplayName("throws ValidationException when limit exceeds 50")
        void throwsOnLimitAboveMax() {
            assertThatThrownBy(() -> crawlerService.crawlBatch(51))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("limit must be between 1 and 50");
        }

        @Test
        @DisplayName("returns crawled=0 when no targets are available")
        void returnsZeroWhenNoTargets() {
            when(pullTargetRepository.findNextAvailableAndLock()).thenReturn(Optional.empty());

            var result = crawlerService.crawlBatch(10);

            assertThat(result.getCrawled()).isZero();
        }

        @Test
        @DisplayName("crawls up to the requested limit")
        void respectsLimit() {
            lenient().when(clients.stream()).thenReturn(Stream.of(mockClient));
            lenient().when(mockClient.supports("greenhouse")).thenReturn(true);
            when(pullTargetRepository.findNextAvailableAndLock())
                    .thenReturn(Optional.of(sampleTarget))
                    .thenReturn(Optional.empty());
            when(mockClient.crawl(any())).thenReturn(PullResult.success(List.of()));

            var result = crawlerService.crawlBatch(5);

            assertThat(result.getCrawled()).isEqualTo(1);
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

        private JobPost buildJob() {
            return JobPost.builder()
                    .id(UUID.randomUUID())
                    .targetId(sampleTarget.getId())
                    .title("Engineer")
                    .url("https://example.com/job/1")
                    .contentHash("hash-abc")
                    .city("Madrid")
                    .country("Spain")
                    .build();
        }
    }
}
