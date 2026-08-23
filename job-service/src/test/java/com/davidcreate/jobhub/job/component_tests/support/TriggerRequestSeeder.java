package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Story #583 / ADR 0033: {@code TriggerRequestEntity} is now fully
 * {@code insertable = false, updatable = false} on every column (crawler-service is
 * the sole writer of {@code crawler.trigger_request}). Component tests that need to
 * seed a row directly (simulating one crawler-service already wrote) go around the
 * mapped entity's own write path via a native INSERT, same technique already used
 * pre-#583 for the {@code progress_*} columns.
 */
public final class TriggerRequestSeeder {

    private TriggerRequestSeeder() {
    }

    public static UUID insert(EntityManager em, String kind, String status, OffsetDateTime requestedAt,
                               OffsetDateTime startedAt, OffsetDateTime finishedAt, String resultSummary,
                               String errorReason) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "INSERT INTO crawler.trigger_request "
                                + "(id, kind, status, requested_at, started_at, finished_at, result_summary, error_reason) "
                                + "VALUES (:id, :kind, :status, :requestedAt, :startedAt, :finishedAt, :resultSummary, :errorReason)")
                .setParameter("id", id)
                .setParameter("kind", kind)
                .setParameter("status", status)
                .setParameter("requestedAt", requestedAt)
                .setParameter("startedAt", startedAt)
                .setParameter("finishedAt", finishedAt)
                .setParameter("resultSummary", resultSummary)
                .setParameter("errorReason", errorReason)
                .executeUpdate());
        return id;
    }
}
