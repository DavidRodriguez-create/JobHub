package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.PullTargetEntity;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import com.davidcreate.jobhub.crawler.domain.model.PullTarget;
import com.davidcreate.jobhub.crawler.domain.model.PullTargetStatus;
import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.crawler.domain.port.out.PullTargetRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-513-B17/B18 (ADR 0029, story #513): the per-row/statement adapter lines are suppressed at
 * the default level, and appear at DEBUG when the category is opened up. Two-phase per class:
 * phase 1 asserts suppression under the default test config (the category inherits
 * {@code quarkus.log.category."com.davidcreate.jobhub.crawler.adapter.out".level}, which
 * defaults to INFO since no {@code CRAWLER_ADAPTER_LOG_LEVEL} is set and the test profile
 * doesn't override it); phase 2 simulates the env-var override by raising the concrete class's
 * logger level directly (JBoss LogManager categories are ordinary mutable {@code Logger}
 * objects at runtime), proving the DEBUG line exists and would be emitted. No prod seam added,
 * same technique as {@code RequestLoggingFilterTest}/{@code CrawlerServiceLoggingTest}.
 */
@QuarkusTest
@DisplayName("Adapter log-level demotion (ADR 0029, story #513)")
class AdapterLogLevelComponentTest {

    private static final UUID TARGET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002");

    @Inject
    JobPostRepository jobPostRepository;

    @Inject
    PullTargetRepository pullTargetRepository;

    @Inject
    EntityManager entityManager;

    private Logger targetLogger;
    private CapturingHandler handler;

    @AfterEach
    void tearDown() {
        if (targetLogger != null && handler != null) {
            targetLogger.removeHandler(handler);
            targetLogger.setLevel(null);
        }
    }

    @Test
    @DisplayName("TC-513-B17: JobPostPanacheRepository's UPSERT line is suppressed at the default "
            + "level, and appears at DEBUG when the category is opened up")
    void jobPostUpsertLineIsSuppressedThenDebugAtOverride() {
        attachTo("com.davidcreate.jobhub.crawler.adapter.out.persistence.JobPostPanacheRepository");

        // Phase 1: default level, the UPSERT line must not appear.
        jobPostRepository.saveAll(List.of(newJob("adapter-log-level-001")));
        assertThat(matching(handler.records, "UPSERT crawler.job_post")).isEmpty();

        // Phase 2: simulate CRAWLER_ADAPTER_LOG_LEVEL=DEBUG by opening the logger directly.
        targetLogger.setLevel(Level.ALL);
        jobPostRepository.saveAll(List.of(newJob("adapter-log-level-002")));

        List<LogRecord> debugLines = matching(handler.records, "UPSERT crawler.job_post");
        assertThat(debugLines).hasSize(1);
        assertThat(debugLines.get(0).getMessage()).isEqualTo("UPSERT crawler.job_post inserted=1 updated=0");
        assertThat(debugLines.get(0).getLevel().intValue()).isLessThan(Level.INFO.intValue());
    }

    @Test
    @DisplayName("TC-513-B18: PullTargetPanacheRepository's UPDATE line is suppressed at the "
            + "default level, and appears at DEBUG when the category is opened up")
    void pullTargetUpdateLineIsSuppressedThenDebugAtOverride() {
        attachTo("com.davidcreate.jobhub.crawler.adapter.out.persistence.PullTargetPanacheRepository");
        UUID id = seedPullTarget();
        PullTarget locked = pullTargetRepository.findTargetById(id).orElseThrow();
        locked.recordSuccess(OffsetDateTime.now().plusHours(1));

        // Phase 1: default level, the UPDATE line must not appear.
        // PullTargetPanacheRepository.save() has no @Transactional of its own, so the
        // caller must supply the transaction (same pattern as TriggerRequestPanacheRepositoryComponentTest).
        QuarkusTransaction.requiringNew().run(() -> pullTargetRepository.save(locked));
        assertThat(matching(handler.records, "UPDATE crawler.pull_target")).isEmpty();

        // Phase 2: simulate CRAWLER_ADAPTER_LOG_LEVEL=DEBUG by opening the logger directly.
        targetLogger.setLevel(Level.ALL);
        QuarkusTransaction.requiringNew().run(() -> pullTargetRepository.save(locked));

        List<LogRecord> debugLines = matching(handler.records, "UPDATE crawler.pull_target");
        assertThat(debugLines).hasSize(1);
        assertThat(debugLines.get(0).getMessage())
                .isEqualTo("UPDATE crawler.pull_target id=" + id + " status=active");
        assertThat(debugLines.get(0).getLevel().intValue()).isLessThan(Level.INFO.intValue());
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private void attachTo(String category) {
        targetLogger = Logger.getLogger(category);
        handler = new CapturingHandler();
        targetLogger.addHandler(handler);
    }

    private UUID seedPullTarget() {
        // PullTargetEntity.id is @GeneratedValue: assigning it before persist() makes Hibernate
        // treat the row as detached, so let the DB generate it and read it back afterward.
        return QuarkusTransaction.requiringNew().call(() -> {
            PullTargetEntity entity = new PullTargetEntity();
            entity.sourceType = "greenhouse";
            entity.companyName = "AdapterLogCo";
            entity.token = "adapter-log-co";
            entity.pullPriority = 100;
            entity.nextPullAfter = OffsetDateTime.now();
            entity.status = PullTargetStatus.ACTIVE.name().toLowerCase();
            entity.statusChangedAt = OffsetDateTime.now();
            entity.consecutiveFailures = 0;
            entity.createdAt = OffsetDateTime.now();
            entity.updatedAt = OffsetDateTime.now();
            entityManager.persist(entity);
            entityManager.flush();
            return entity.id;
        });
    }

    private JobPost newJob(String contentHash) {
        return JobPost.builder()
                .targetId(TARGET_ID)
                .title("Adapter Log Level Job")
                .url("https://example.com/adapter-log-level/" + contentHash)
                .description("Description")
                .contentHash(contentHash)
                .build();
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
    }
}
