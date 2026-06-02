package com.davidcreate.jobhub.job.unit_tests.domain.service;

import com.davidcreate.jobhub.job.domain.model.FacetValue;
import com.davidcreate.jobhub.job.domain.model.JobFacets;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import com.davidcreate.jobhub.job.domain.model.JobSearchQuery;
import com.davidcreate.jobhub.job.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.job.domain.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobService Unit Tests")
class JobServiceTest {

    @Mock
    JobPostRepository jobPostRepository;

    @InjectMocks
    JobService jobService;

    private JobPost sampleJob;

    @BeforeEach
    void setUp() {
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
        @DisplayName("delegates to repository and returns its count")
        void delegatesAndReturnsCount() {
            JobSearchQuery query = JobSearchQuery.builder().build();
            when(jobPostRepository.count(query)).thenReturn(42L);

            long result = jobService.count(query);

            assertThat(result).isEqualTo(42L);
            verify(jobPostRepository).count(query);
        }

        @Test
        @DisplayName("returns zero when no rows match")
        void returnsZeroOnNoMatch() {
            JobSearchQuery query = JobSearchQuery.builder().keyword("nothing").build();
            when(jobPostRepository.count(query)).thenReturn(0L);

            long result = jobService.count(query);

            assertThat(result).isZero();
        }
    }

    @Nested
    @DisplayName("getFacets()")
    class GetFacets {

        @Test
        @DisplayName("delegates to repository and returns its facets")
        void delegatesAndReturnsFacets() {
            JobFacets expected = new JobFacets(
                    List.of(new FacetValue("Stripe", 3L)),
                    List.of(new FacetValue("Spain", 4L)),
                    List.of(new FacetValue("English", 5L)),
                    List.of(new FacetValue("full-time", 4L)),
                    60000, 100000);
            when(jobPostRepository.facets()).thenReturn(expected);

            JobFacets result = jobService.getFacets();

            assertThat(result).isSameAs(expected);
            verify(jobPostRepository).facets();
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
