package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.adapter.in.scheduler.TriggerRequestScheduler;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.TriggerRequestEntity;
import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
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
 * Story #537 (C20): no regression to job_post's existing per-row enrichment retry rule
 * (enrichment_attempts / enrichment_status) from the new global scheduled-pass backoff.
 *
 * <p>C20's spec (docs/test-cases/537-enrichment-backoff-test-cases.md) names this
 * {@code TriggerRequestResourceFailureComponentTest}, implying a REST resource in
 * crawler-service. crawler-service has no published port (CLAUDE.md): the admin trigger
 * REST endpoint lives in job-service (job-service's {@code AdminTriggerService}), which
 * only inserts a queued {@code crawler.trigger_request} row for this service's
 * {@link TriggerRequestScheduler} to pick up. "Trigger enrichment via admin endpoint
 * twice" is reproduced here at that real entry point: two queued ENRICHMENT rows, each
 * processed by {@code scheduler.run()} (this is a QAE case/service-boundary gap, raised
 * in the handoff, not silently worked around).
 */
@QuarkusTest
@DisplayName("TriggerRequestScheduler Enrichment Retry Component Tests")
class TriggerRequestSchedulerEnrichmentRetryComponentTest {

    private static final UUID TARGET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001");

    @Inject
    TriggerRequestScheduler scheduler;

    @Inject
    EntityManager entityManager;

    @InjectMock
    JobEnricher jobEnricher;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createQuery("delete from TriggerRequestEntity").executeUpdate();
            entityManager.createQuery("delete from JobPostEntity").executeUpdate();
        });
        when(jobEnricher.enrich(any(), any(), any(), any())).thenThrow(new RuntimeException("model down"));
    }

    private UUID insertPendingJobPost() {
        return QuarkusTransaction.requiringNew().call(() -> {
            JobPostEntity job = new JobPostEntity();
            job.targetId = TARGET_ID;
            job.title = "Backend Engineer";
            job.url = "https://example.com/jobs/" + UUID.randomUUID();
            job.description = "desc";
            job.enrichmentStatus = "pending";
            job.enrichmentAttempts = 0;
            job.firstSeenAt = OffsetDateTime.now();
            job.lastSeenAt = OffsetDateTime.now();
            entityManager.persist(job);
            entityManager.flush();
            return job.id;
        });
    }

    private void insertQueuedEnrichmentRow() {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            TriggerRequestEntity entity = new TriggerRequestEntity();
            entity.id = id;
            entity.kind = TriggerKind.ENRICHMENT.value();
            entity.status = "queued";
            entity.requestedAt = OffsetDateTime.now().minusMinutes(1);
            entityManager.persist(entity);
        });
    }

    private JobPostEntity findJobPost(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.find(JobPostEntity.class, id));
    }

    @Test
    @DisplayName("C20: enrichment_status stays pending across two admin-triggered failing passes (existing retry rule, no regression)")
    void enrichmentStatusFollowsExistingRetryRuleAcrossTwoAdminTriggeredPasses() {
        UUID jobPostId = insertPendingJobPost();

        insertQueuedEnrichmentRow();
        scheduler.run();

        JobPostEntity afterFirstPass = findJobPost(jobPostId);
        assertThat(afterFirstPass.enrichmentStatus).isEqualTo("pending");
        assertThat(afterFirstPass.enrichmentAttempts).isEqualTo((short) 1);

        insertQueuedEnrichmentRow();
        scheduler.run();

        JobPostEntity afterSecondPass = findJobPost(jobPostId);
        assertThat(afterSecondPass.enrichmentStatus).isEqualTo("pending");
        assertThat(afterSecondPass.enrichmentAttempts).isEqualTo((short) 2);
    }
}
