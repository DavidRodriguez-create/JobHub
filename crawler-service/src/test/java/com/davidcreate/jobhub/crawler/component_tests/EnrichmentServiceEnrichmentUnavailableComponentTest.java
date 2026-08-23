package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.crawler.domain.exception.EnrichmentUnavailableException;
import com.davidcreate.jobhub.crawler.domain.port.in.EnrichJobsUseCase;
import com.davidcreate.jobhub.crawler.domain.port.out.JobEnricher;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Story #556 (D2): {@code EnrichmentUnavailableException} (transient provider
 * exhaustion) must not blacklist a good posting, unlike a genuine per-posting
 * failure, which keeps the existing retry-then-fail rule.
 */
@QuarkusTest
@DisplayName("EnrichmentService EnrichmentUnavailableException Component Tests")
class EnrichmentServiceEnrichmentUnavailableComponentTest {

    private static final UUID TARGET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002");

    @Inject
    EnrichJobsUseCase enrichJobsUseCase;

    @Inject
    EntityManager entityManager;

    @InjectMock
    JobEnricher jobEnricher;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() ->
                entityManager.createQuery("delete from JobPostEntity").executeUpdate());
    }

    private UUID insertPendingJobPost(short enrichmentAttempts) {
        return QuarkusTransaction.requiringNew().call(() -> {
            JobPostEntity job = new JobPostEntity();
            job.targetId = TARGET_ID;
            job.title = "Backend Engineer";
            job.url = "https://example.com/jobs/" + UUID.randomUUID();
            job.description = "desc";
            job.enrichmentStatus = "pending";
            job.enrichmentAttempts = enrichmentAttempts;
            job.firstSeenAt = OffsetDateTime.now();
            job.lastSeenAt = OffsetDateTime.now();
            entityManager.persist(job);
            entityManager.flush();
            return job.id;
        });
    }

    private JobPostEntity findJobPost(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.find(JobPostEntity.class, id));
    }

    @Test
    @DisplayName("C13: EnrichmentUnavailableException leaves enrichment_attempts and status untouched")
    void transientUnavailabilityDoesNotIncrementAttemptsOrFailRow() {
        when(jobEnricher.enrich(any(), any(), any(), any()))
                .thenThrow(new EnrichmentUnavailableException("no provider reachable"));
        UUID jobPostId = insertPendingJobPost((short) 0);

        enrichJobsUseCase.enrichPending(5, null);

        JobPostEntity after = findJobPost(jobPostId);
        assertThat(after.enrichmentAttempts).isEqualTo((short) 0);
        assertThat(after.enrichmentStatus).isEqualTo("pending");
    }

    @Test
    @DisplayName("C14: a genuine failure at maxAttempts-1 increments attempts and flips status to failed (regression guard)")
    void genuineFailureAtMaxAttemptsFlipsStatusToFailed() {
        when(jobEnricher.enrich(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("genuinely unusable output"));
        UUID jobPostId = insertPendingJobPost((short) 2); // max-attempts default = 3

        enrichJobsUseCase.enrichPending(5, null);

        JobPostEntity after = findJobPost(jobPostId);
        assertThat(after.enrichmentAttempts).isEqualTo((short) 3);
        assertThat(after.enrichmentStatus).isEqualTo("failed");
    }
}
