package com.davidcreate.jobhub.job.unit_tests.domain.service;

import com.davidcreate.jobhub.job.domain.model.CountMode;
import com.davidcreate.jobhub.job.domain.model.FacetValue;
import com.davidcreate.jobhub.job.domain.model.JobCount;
import com.davidcreate.jobhub.job.domain.model.JobFacets;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import com.davidcreate.jobhub.job.domain.model.JobSearchQuery;
import com.davidcreate.jobhub.job.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.job.domain.service.CountCache;
import com.davidcreate.jobhub.job.domain.service.CrawlGenerationStamp;
import com.davidcreate.jobhub.job.domain.service.FacetCache;
import com.davidcreate.jobhub.job.domain.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobService Unit Tests")
class JobServiceTest {

    @Mock
    JobPostRepository jobPostRepository;

    @Mock
    CrawlGenerationStamp crawlGenerationStamp;

    private JobService jobService;

    private JobPost sampleJob;

    @BeforeEach
    void setUp() {
        // Real, disabled CountCache/FacetCache: always misses, so every count(...)/
        // getFacets(...) call in this test class exercises the real compute() path with
        // no cache stubbing needed. mode=hybrid, exact-threshold=1000 (defaults) unless a
        // nested class overrides it. crawlGenerationStamp is left unstubbed here (each
        // GetFacets test stubs it locally) so Search/Count/GetById nested tests, which
        // never touch facets, never trigger an unnecessary-stubbing failure.
        CountCache disabledCountCache = new CountCache(false, Duration.ofSeconds(30), 1000);
        FacetCache disabledFacetCache = new FacetCache(false, Duration.ofSeconds(60), 500);
        jobService = new JobService(jobPostRepository, disabledCountCache, disabledFacetCache,
                crawlGenerationStamp, "hybrid", 1000L);

        sampleJob = JobPost.builder()
                .id(UUID.randomUUID())
                .targetId(UUID.randomUUID())
                .title("Java Developer")
                .url("https://example.com/jobs/1")
                .description("Backend role")
                .contentHash("hash-1")
                .city("Madrid")
                .country("Spain")
                .firstSeenAt(OffsetDateTime.parse("2024-01-01T10:00:00Z"))
                .lastSeenAt(OffsetDateTime.parse("2024-01-10T10:00:00Z"))
                .build();
    }

    @Nested
    @DisplayName("search(JobSearchQuery)")
    class Search {

        @Test
        @DisplayName("delegates to repository and returns its results")
        void delegatesAndReturnsResults() {
            JobSearchQuery query = JobSearchQuery.builder().keyword("Java").build();
            List<JobPost> expected = List.of(sampleJob);
            when(jobPostRepository.search(query)).thenReturn(expected);

            List<JobPost> result = jobService.search(query);

            assertThat(result).isEqualTo(expected);
            verify(jobPostRepository).search(query);
        }

        @Test
        @DisplayName("returns empty list when repository finds nothing (no 404 thrown)")
        void returnsEmptyListWhenNoneMatch() {
            JobSearchQuery query = JobSearchQuery.builder().keyword("nothing").build();
            when(jobPostRepository.search(query)).thenReturn(List.of());

            List<JobPost> result = jobService.search(query);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("passes the exact query argument to the repository")
        void passesQueryArgument() {
            JobSearchQuery query = JobSearchQuery.builder()
                    .keyword("Java")
                    .locations(List.of("Madrid, Spain"))
                    .page(2)
                    .size(50)
                    .build();
            when(jobPostRepository.search(query)).thenReturn(List.of(sampleJob));

            jobService.search(query);

            verify(jobPostRepository).search(query);
        }
    }

    @Nested
    @DisplayName("count(JobSearchQuery)")
    class Count {

        @Test
        @DisplayName("delegates to repository and returns its count (estimate <= threshold -> exact)")
        void delegatesAndReturnsCount() {
            JobSearchQuery query = JobSearchQuery.builder().build();
            when(jobPostRepository.count(query)).thenReturn(42L);

            JobCount result = jobService.count(query);

            assertThat(result.value()).isEqualTo(42L);
            assertThat(result.estimate()).isFalse();
            verify(jobPostRepository).count(query);
        }

        @Test
        @DisplayName("returns zero when no rows match")
        void returnsZeroOnNoMatch() {
            JobSearchQuery query = JobSearchQuery.builder().keyword("nothing").build();
            when(jobPostRepository.count(query)).thenReturn(0L);

            JobCount result = jobService.count(query);

            assertThat(result.value()).isZero();
            assertThat(result.estimate()).isFalse();
        }

        @Test
        @DisplayName("mode=hybrid: estimate above threshold returns the estimate, never calling exact count")
        void hybridAboveThresholdReturnsEstimate() {
            CountCache disabledCountCache = new CountCache(false, Duration.ofSeconds(30), 1000);
            FacetCache disabledFacetCache = new FacetCache(false, Duration.ofSeconds(60), 500);
            JobService hybridService = new JobService(jobPostRepository, disabledCountCache, disabledFacetCache,
                    crawlGenerationStamp, "hybrid", 10L);
            JobSearchQuery query = JobSearchQuery.builder().build();
            when(jobPostRepository.estimateCount(query)).thenReturn(50L);

            JobCount result = hybridService.count(query);

            assertThat(result.value()).isEqualTo(50L);
            assertThat(result.estimate()).isTrue();
            verify(jobPostRepository, never()).count(query);
        }

        @Test
        @DisplayName("mode=exact: never calls estimateCount, even with a threshold that would otherwise force estimate")
        void exactModeNeverCallsEstimateCount() {
            CountCache disabledCountCache = new CountCache(false, Duration.ofSeconds(30), 1000);
            FacetCache disabledFacetCache = new FacetCache(false, Duration.ofSeconds(60), 500);
            JobService exactService = new JobService(jobPostRepository, disabledCountCache, disabledFacetCache,
                    crawlGenerationStamp, "exact", 0L);
            JobSearchQuery query = JobSearchQuery.builder().build();
            when(jobPostRepository.count(query)).thenReturn(11L);

            JobCount result = exactService.count(query);

            assertThat(result.value()).isEqualTo(11L);
            assertThat(result.estimate()).isFalse();
            verify(jobPostRepository, never()).estimateCount(any());
        }

        @Test
        @DisplayName("mode=estimate: always returns the estimate, never calling exact count, even for a small result set")
        void estimateModeAlwaysReturnsEstimate() {
            CountCache disabledCountCache = new CountCache(false, Duration.ofSeconds(30), 1000);
            FacetCache disabledFacetCache = new FacetCache(false, Duration.ofSeconds(60), 500);
            JobService estimateService = new JobService(jobPostRepository, disabledCountCache, disabledFacetCache,
                    crawlGenerationStamp, "estimate", 1000L);
            JobSearchQuery query = JobSearchQuery.builder().build();
            when(jobPostRepository.estimateCount(query)).thenReturn(1L);

            JobCount result = estimateService.count(query);

            assertThat(result.value()).isEqualTo(1L);
            assertThat(result.estimate()).isTrue();
            verify(jobPostRepository, never()).count(query);
        }
    }

    @Nested
    @DisplayName("getFacets(JobSearchQuery)")
    class GetFacets {

        @Test
        @DisplayName("delegates to repository with the query and returns its facets")
        void delegatesAndReturnsFacets() {
            JobFacets expected = new JobFacets(
                    List.of(new FacetValue("Stripe", 4L)),
                    List.of(new FacetValue("Spain", 5L)),
                    List.of(new FacetValue("English", 6L)),
                    List.of(new FacetValue("full-time", 5L)),
                    List.of(new FacetValue("senior", 3L)),
                    60000, 110000);
            JobSearchQuery query = JobSearchQuery.builder().build();
            when(jobPostRepository.facets(query)).thenReturn(expected);

            JobFacets result = jobService.getFacets(query);

            assertThat(result).isSameAs(expected);
            verify(jobPostRepository).facets(query);
        }

        @Test
        @DisplayName("passes the exact query (with active filters) to the repository")
        void passesQueryWithFilters() {
            JobSearchQuery query = JobSearchQuery.builder()
                    .locations(List.of("Germany"))
                    .build();
            JobFacets expected = new JobFacets(
                    List.of(new FacetValue("Spotify", 1L)),
                    List.of(new FacetValue("Spain", 5L), new FacetValue("Germany", 1L)),
                    List.of(new FacetValue("English", 1L), new FacetValue("German", 1L)),
                    List.of(new FacetValue("full-time", 1L)),
                    List.of(new FacetValue("senior", 1L)),
                    65000, 85000);
            when(jobPostRepository.facets(query)).thenReturn(expected);

            JobFacets result = jobService.getFacets(query);

            assertThat(result).isSameAs(expected);
            verify(jobPostRepository).facets(query);
        }

        private JobService withEnabledFacetCache() {
            FacetCache facetCache = new FacetCache(true, Duration.ofSeconds(60), 500);
            CountCache disabledCountCache = new CountCache(false, Duration.ofSeconds(30), 1000);
            return new JobService(jobPostRepository, disabledCountCache, facetCache,
                    crawlGenerationStamp, "hybrid", 1000L);
        }

        private JobFacets sampleFacets() {
            return new JobFacets(
                    List.of(new FacetValue("Stripe", 4L)),
                    List.of(new FacetValue("Spain", 5L)),
                    List.of(new FacetValue("English", 6L)),
                    List.of(new FacetValue("full-time", 5L)),
                    List.of(new FacetValue("senior", 3L)),
                    60000, 110000);
        }

        @Test
        @DisplayName("FC332-U-23 (AC-332-1): miss -> repository invoked exactly once, result returned and stored in the cache")
        void missComputesOnceAndStores() {
            JobService service = withEnabledFacetCache();
            when(crawlGenerationStamp.current()).thenReturn(1L);
            JobSearchQuery query = JobSearchQuery.builder().keyword("java").build();
            JobFacets expected = sampleFacets();
            when(jobPostRepository.facets(query)).thenReturn(expected);

            JobFacets result = service.getFacets(query);

            assertThat(result).isSameAs(expected);
            verify(jobPostRepository, times(1)).facets(query);
        }

        @Test
        @DisplayName("FC332-U-24 (AC-332-6): second call, identical query, stamp unchanged -> repository never invoked again")
        void repeatedIdenticalQuerySameGenerationHitsCache() {
            JobService service = withEnabledFacetCache();
            when(crawlGenerationStamp.current()).thenReturn(1L);
            JobSearchQuery query = JobSearchQuery.builder().keyword("java").build();
            JobFacets expected = sampleFacets();
            when(jobPostRepository.facets(query)).thenReturn(expected);

            JobFacets first = service.getFacets(query);
            JobFacets second = service.getFacets(query);

            assertThat(first).isSameAs(expected);
            assertThat(second).isSameAs(expected);
            verify(jobPostRepository, times(1)).facets(query);
        }

        @Test
        @DisplayName("FC332-U-25 (AC-332-8/9): second call, identical query, stamp advances -> repository invoked twice (recompute)")
        void generationAdvanceTriggersRecompute() {
            JobService service = withEnabledFacetCache();
            when(crawlGenerationStamp.current()).thenReturn(1L, 2L);
            JobSearchQuery query = JobSearchQuery.builder().keyword("java").build();
            JobFacets first = sampleFacets();
            JobFacets second = sampleFacets();
            when(jobPostRepository.facets(query)).thenReturn(first, second);

            JobFacets result1 = service.getFacets(query);
            JobFacets result2 = service.getFacets(query);

            assertThat(result1).isSameAs(first);
            assertThat(result2).isSameAs(second);
            verify(jobPostRepository, times(2)).facets(query);
        }

        @Test
        @DisplayName("FC332-U-26 (regression): miss with an active-filter query -> repository receives the exact query object")
        void missWithActiveFilterQueryPassesExactQuery() {
            JobService service = withEnabledFacetCache();
            when(crawlGenerationStamp.current()).thenReturn(1L);
            JobSearchQuery query = JobSearchQuery.builder().locations(List.of("Germany")).build();
            JobFacets expected = sampleFacets();
            when(jobPostRepository.facets(query)).thenReturn(expected);

            service.getFacets(query);

            verify(jobPostRepository).facets(query);
        }

        @Test
        @DisplayName("FC332-U-27 (AC-332-17): real, disabled FacetCache -> repository invoked on every call, never reused")
        void disabledFacetCacheNeverReuses() {
            FacetCache disabledFacetCache = new FacetCache(false, Duration.ofSeconds(60), 500);
            CountCache disabledCountCache = new CountCache(false, Duration.ofSeconds(30), 1000);
            JobService service = new JobService(jobPostRepository, disabledCountCache, disabledFacetCache,
                    crawlGenerationStamp, "hybrid", 1000L);
            when(crawlGenerationStamp.current()).thenReturn(1L);
            JobSearchQuery query = JobSearchQuery.builder().keyword("java").build();
            when(jobPostRepository.facets(query)).thenReturn(sampleFacets());

            service.getFacets(query);
            service.getFacets(query);

            verify(jobPostRepository, times(2)).facets(query);
        }

        @Test
        @DisplayName("FC332-U-28 (AC-332-12): two distinct query combos, same generation throughout -> each computed once, no cross-combo leakage")
        void distinctCombosEachComputedOnceWithNoLeakage() {
            JobService service = withEnabledFacetCache();
            when(crawlGenerationStamp.current()).thenReturn(1L);
            JobSearchQuery queryA = JobSearchQuery.builder().locations(List.of("Germany")).build();
            JobSearchQuery queryB = JobSearchQuery.builder().locations(List.of("Spain")).build();
            JobFacets facetsA = sampleFacets();
            JobFacets facetsB = sampleFacets();
            when(jobPostRepository.facets(queryA)).thenReturn(facetsA);
            when(jobPostRepository.facets(queryB)).thenReturn(facetsB);

            JobFacets resultA = service.getFacets(queryA);
            JobFacets resultB = service.getFacets(queryB);

            assertThat(resultA).isSameAs(facetsA);
            assertThat(resultB).isSameAs(facetsB);
            verify(jobPostRepository, times(1)).facets(queryA);
            verify(jobPostRepository, times(1)).facets(queryB);
        }

        @Test
        @DisplayName("FC332-U-29 (AC-332-13): alternate A, B, A (same generation throughout) -> repository invoked twice; third call reuses A's own cached value")
        void alternatingCombosReuseOwnCachedEntryNotTheOtherCombosValue() {
            JobService service = withEnabledFacetCache();
            when(crawlGenerationStamp.current()).thenReturn(1L);
            JobSearchQuery queryA = JobSearchQuery.builder().locations(List.of("Germany")).build();
            JobSearchQuery queryB = JobSearchQuery.builder().locations(List.of("Spain")).build();
            JobFacets facetsA = sampleFacets();
            JobFacets facetsB = sampleFacets();
            when(jobPostRepository.facets(queryA)).thenReturn(facetsA);
            when(jobPostRepository.facets(queryB)).thenReturn(facetsB);

            JobFacets firstA = service.getFacets(queryA);
            service.getFacets(queryB);
            JobFacets secondA = service.getFacets(queryA);

            assertThat(secondA).isSameAs(firstA);
            assertThat(secondA).isNotSameAs(facetsB);
            verify(jobPostRepository, times(1)).facets(queryA);
            verify(jobPostRepository, times(1)).facets(queryB);
        }
    }

    @Nested
    @DisplayName("getById(UUID)")
    class GetById {

        @Test
        @DisplayName("returns the job when the repository finds it")
        void returnsJobWhenFound() {
            UUID id = sampleJob.getId();
            when(jobPostRepository.findJobById(id)).thenReturn(Optional.of(sampleJob));

            Optional<JobPost> result = jobService.getById(id);

            assertThat(result).contains(sampleJob);
            verify(jobPostRepository).findJobById(id);
        }

        @Test
        @DisplayName("returns empty Optional when the repository finds nothing")
        void returnsEmptyWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(jobPostRepository.findJobById(id)).thenReturn(Optional.empty());

            Optional<JobPost> result = jobService.getById(id);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("does not invoke search or count branches")
        void doesNotInvokeOtherMethods() {
            UUID id = UUID.randomUUID();
            when(jobPostRepository.findJobById(id)).thenReturn(Optional.empty());

            jobService.getById(id);

            verify(jobPostRepository, never()).search(any());
            verify(jobPostRepository, never()).count(any());
        }
    }
}
