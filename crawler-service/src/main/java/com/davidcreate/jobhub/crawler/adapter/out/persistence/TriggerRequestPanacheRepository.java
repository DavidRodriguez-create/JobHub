package com.davidcreate.jobhub.crawler.adapter.out.persistence;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.TriggerRequestEntity;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.mapper.TriggerRequestMapper;
import com.davidcreate.jobhub.crawler.domain.model.CrawlProgress;
import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.model.TriggerOrigin;
import com.davidcreate.jobhub.crawler.domain.model.TriggerOutcome;
import com.davidcreate.jobhub.crawler.domain.model.TriggerRequest;
import com.davidcreate.jobhub.crawler.domain.model.TriggerStatus;
import com.davidcreate.jobhub.crawler.domain.exception.ConflictException;
import com.davidcreate.jobhub.crawler.domain.port.out.CrawlProgressRecorder;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownFlag;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class TriggerRequestPanacheRepository
        implements TriggerRequestQueue, CrawlProgressRecorder, PanacheRepositoryBase<TriggerRequestEntity, UUID> {

    private static final Logger LOG = Logger.getLogger(TriggerRequestPanacheRepository.class);
    private static final String ACTIVE_KIND_STATUS_CONSTRAINT = "uq_trigger_request_active_kind_status";

    private final TriggerRequestMapper mapper;

    @Override
    @Transactional
    public Optional<TriggerRequest> claimNext(TriggerKind kind) {
        @SuppressWarnings("unchecked")
        List<TriggerRequestEntity> result = getEntityManager()
                .createNativeQuery("""
                        SELECT * FROM crawler.trigger_request
                        WHERE kind = :kind
                        AND status = 'queued'
                        ORDER BY requested_at
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                        """, TriggerRequestEntity.class)
                .setParameter("kind", kind.value())
                .getResultList();

        return result.stream().findFirst().map(entity -> {
            entity.status = "running";
            entity.startedAt = OffsetDateTime.now();
            persistAndFlush(entity);
            LOG.infof("CLAIM crawler.trigger_request id=%s kind=%s", entity.id, entity.kind);
            return mapper.toDomain(entity);
        });
    }

    @Override
    @Transactional
    public void markRunning(UUID id) {
        findByIdOptional(id).ifPresent(entity -> {
            entity.status = "running";
            if (entity.startedAt == null) {
                entity.startedAt = OffsetDateTime.now();
            }
            // Zero the counters and clear the current-target fields at run start (ADR 0029):
            // a fresh run must never show a previous run's leftover progress.
            entity.progressTargetsVisited = 0;
            entity.progressNewPosts = 0;
            entity.progressCurrentCompany = null;
            entity.progressCurrentSourceType = null;
            persistAndFlush(entity);
            LOG.infof("UPDATE crawler.trigger_request id=%s status=running", id);
        });
    }

    @Override
    @Transactional
    public void markDone(UUID id, String status, String outcome, String resultSummary, String errorReason) {
        findByIdOptional(id).ifPresent(entity -> {
            entity.status = status;
            entity.outcome = outcome;
            entity.finishedAt = OffsetDateTime.now();
            if (entity.startedAt == null) {
                entity.startedAt = OffsetDateTime.now();
            }
            entity.resultSummary = resultSummary;
            entity.errorReason = errorReason;
            // Terminal: the final counters remain as the snapshot, but nothing is
            // crawling any more (ADR 0029).
            entity.progressCurrentCompany = null;
            entity.progressCurrentSourceType = null;
            persistAndFlush(entity);
            LOG.infof("UPDATE crawler.trigger_request id=%s status=%s outcome=%s", id, status, outcome);
        });
    }

    @Override
    public boolean hasRunning(TriggerKind kind) {
        long count = count("kind = ?1 and status = ?2", kind.value(), TriggerStatus.RUNNING.value());
        return count > 0;
    }

    @Override
    public boolean hasActive(TriggerKind kind) {
        long count = count("kind = ?1 and status in (?2, ?3)",
                kind.value(), TriggerStatus.QUEUED.value(), TriggerStatus.RUNNING.value());
        return count > 0;
    }

    @Override
    @Transactional
    public TriggerRequest enqueue(TriggerKind kind, TriggerOrigin origin, UUID requestedBy) {
        TriggerRequestEntity entity = new TriggerRequestEntity();
        entity.id = UUID.randomUUID();
        entity.kind = kind.value();
        entity.status = TriggerStatus.QUEUED.value();
        entity.origin = origin.value();
        entity.requestedBy = requestedBy;
        entity.requestedAt = OffsetDateTime.now();
        try {
            persistAndFlush(entity);
        } catch (PersistenceException e) {
            if (isActiveKindStatusViolation(e)) {
                throw new ConflictException("A queued " + kind.value() + " request already exists", e);
            }
            throw e;
        }
        LOG.infof("ENQUEUE crawler.trigger_request id=%s kind=%s origin=%s", entity.id, entity.kind, entity.origin);
        return mapper.toDomain(entity);
    }

    /**
     * Narrows the {@link PersistenceException} caught in {@link #enqueue} to the specific
     * unique-violation this insert can legitimately hit (story #582, blocker 1): any other
     * database fault (connection loss, an unrelated constraint, a deadlock) must surface as a
     * 500, not a false 409 telling the admin a request is already queued.
     */
    private boolean isActiveKindStatusViolation(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String constraintName = cve.getConstraintName();
                if (constraintName != null && constraintName.contains(ACTIVE_KIND_STATUS_CONSTRAINT)) {
                    return true;
                }
                if (cve.getMessage() != null && cve.getMessage().contains(ACTIVE_KIND_STATUS_CONSTRAINT)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    @Transactional
    public Optional<TriggerRequest> cancelActive(TriggerKind kind) {
        // Blocking FOR UPDATE, not SKIP LOCKED (story #582, blocker 2): a targeted cancel must
        // wait for a row the scheduler's own claim loop is holding, not silently skip it and
        // report "nothing active" while a pass is visibly running.
        @SuppressWarnings("unchecked")
        List<TriggerRequestEntity> result = getEntityManager()
                .createNativeQuery("""
                        SELECT * FROM crawler.trigger_request
                        WHERE kind = :kind
                        AND status IN ('queued', 'running')
                        ORDER BY requested_at
                        LIMIT 1
                        FOR UPDATE
                        """, TriggerRequestEntity.class)
                .setParameter("kind", kind.value())
                .getResultList();

        return result.stream().findFirst().map(entity -> {
            if (TriggerStatus.QUEUED.value().equals(entity.status)) {
                entity.status = TriggerStatus.CANCELLED.value();
                entity.outcome = TriggerOutcome.CANCELLED.value();
                entity.finishedAt = OffsetDateTime.now();
                entity.resultSummary = "Cancelled before it started";
            } else {
                entity.status = TriggerStatus.CANCEL_REQUESTED.value();
            }
            persistAndFlush(entity);
            LOG.infof("UPDATE crawler.trigger_request id=%s status=%s (internal cancel)", entity.id, entity.status);
            return mapper.toDomain(entity);
        });
    }

    @Override
    public List<TriggerRequest> findRunning() {
        return list("status", TriggerStatus.RUNNING.value()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void markInterrupted(UUID id, String reason) {
        findByIdOptional(id).ifPresent(entity -> {
            entity.status = TriggerStatus.FAILED.value();
            entity.outcome = TriggerOutcome.INTERRUPTED.value();
            entity.errorReason = reason;
            entity.finishedAt = OffsetDateTime.now();
            entity.progressCurrentCompany = null;
            entity.progressCurrentSourceType = null;
            persistAndFlush(entity);
            LOG.infof("UPDATE crawler.trigger_request id=%s status=failed outcome=interrupted (stale sweep)", id);
        });
    }

    @Override
    @Transactional
    public void reapNonTerminal(String reason) {
        int updated = getEntityManager()
                .createNativeQuery("""
                        UPDATE crawler.trigger_request
                        SET status = 'failed',
                            outcome = 'interrupted',
                            error_reason = :reason,
                            finished_at = COALESCE(finished_at, NOW()),
                            started_at = COALESCE(started_at, NOW()),
                            progress_current_company = NULL,
                            progress_current_source_type = NULL
                        WHERE status IN ('queued', 'running', 'cancel_requested')
                        """)
                .setParameter("reason", reason)
                .executeUpdate();
        if (updated > 0) {
            LOG.infof("REAP crawler.trigger_request: %d non-terminal row(s) interrupted by shutdown", updated);
        }
    }

    @Override
    public boolean isCancelRequested(UUID id) {
        return findByIdOptional(id)
                .map(entity -> TriggerStatus.CANCEL_REQUESTED.value().equals(entity.status))
                .orElse(false);
    }

    @Override
    @Transactional
    public void markCancelled(UUID id, String resultSummary) {
        findByIdOptional(id).ifPresent(entity -> {
            entity.status = TriggerStatus.CANCELLED.value();
            entity.outcome = TriggerOutcome.CANCELLED.value();
            entity.finishedAt = OffsetDateTime.now();
            if (entity.startedAt == null) {
                entity.startedAt = OffsetDateTime.now();
            }
            entity.resultSummary = resultSummary;
            entity.errorReason = null;
            // Terminal: the final counters remain as the snapshot, but nothing is
            // crawling any more (ADR 0029).
            entity.progressCurrentCompany = null;
            entity.progressCurrentSourceType = null;
            persistAndFlush(entity);
            LOG.infof("UPDATE crawler.trigger_request id=%s status=cancelled", id);
        });
    }

    // ─── CrawlProgressRecorder (ADR 0029, story #513) ──────────────────────────
    // Each write runs in its own REQUIRES_NEW transaction, committed independently of the
    // (much longer) per-target transaction: visible to job-service the moment it commits, and
    // never rolled back by a failing target. Try/catch + swallow: a visibility failure must
    // never break a crawl.

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void markCurrentTarget(UUID triggerRequestId, String companyName, String sourceType) {
        if (triggerRequestId == null) {
            return;
        }
        try {
            update("progressCurrentCompany = ?1, progressCurrentSourceType = ?2, progressUpdatedAt = ?3 "
                    + "where id = ?4",
                    companyName, sourceType, OffsetDateTime.now(), triggerRequestId);
        } catch (Exception e) {
            logProgressWriteFailure(triggerRequestId, e);
        }
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordTargetCompleted(UUID triggerRequestId, CrawlProgress progress) {
        if (triggerRequestId == null) {
            return;
        }
        try {
            update("""
                    progressTargetsVisited = ?1, progressNewPosts = ?2,
                    progressCurrentCompany = null, progressCurrentSourceType = null,
                    progressLastCompany = ?3, progressLastSourceType = ?4,
                    progressLastFoundPosts = ?5, progressLastNewPosts = ?6,
                    progressUpdatedAt = ?7
                    where id = ?8
                    """,
                    progress.getTargetsVisited(), progress.getNewPosts(),
                    progress.getLastCompanyName(), progress.getLastSourceType(),
                    progress.getLastFoundPosts(), progress.getLastNewPosts(),
                    OffsetDateTime.now(), triggerRequestId);
        } catch (Exception e) {
            logProgressWriteFailure(triggerRequestId, e);
        }
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void clearCurrentTarget(UUID triggerRequestId) {
        if (triggerRequestId == null) {
            return;
        }
        try {
            update("progressCurrentCompany = null, progressCurrentSourceType = null, progressUpdatedAt = ?1 "
                    + "where id = ?2",
                    OffsetDateTime.now(), triggerRequestId);
        } catch (Exception e) {
            logProgressWriteFailure(triggerRequestId, e);
        }
    }

    /**
     * Story #398 (ADR 0032, D1, 5th pass): a drain-timeout interrupt can land here too,
     * inside a REQUIRES_NEW transaction, once the CDI container is tearing down. Expected
     * once shutdown is up -- log quietly (no stack trace) instead of the loud WARN this
     * used the rest of the time, which remains a real safety net for a genuine progress-
     * write failure while running.
     */
    private void logProgressWriteFailure(UUID triggerRequestId, Exception e) {
        if (ShutdownFlag.isRaised()) {
            LOG.infof("Crawl progress write for %s abandoned during shutdown: %s", triggerRequestId, e.getMessage());
            return;
        }
        LOG.warnf(e, "Failed to record crawl progress for %s", triggerRequestId);
    }
}
