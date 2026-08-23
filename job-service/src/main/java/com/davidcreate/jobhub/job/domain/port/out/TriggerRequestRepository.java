package com.davidcreate.jobhub.job.domain.port.out;

import com.davidcreate.jobhub.job.domain.model.TriggerKind;
import com.davidcreate.jobhub.job.domain.model.TriggerRequest;

import java.util.Optional;

/**
 * Read-only port over {@code crawler.trigger_request} (ADR 0033, ticket #583).
 * crawler-service is the sole writer; job-service keeps {@code SELECT} only, backing
 * the admin status/history panel. Queueing and cancelling go through
 * {@link CrawlerTriggerGateway} instead.
 */
public interface TriggerRequestRepository {

    /** Most recent row for the given kind, or empty if never triggered. */
    Optional<TriggerRequest> findMostRecent(TriggerKind kind);

    /**
     * Story #398 / ADR 0032: the newest row of the given kind in a terminal
     * status ({@code succeeded}, {@code failed}, {@code cancelled}), ordered by
     * {@code finishedAt}. Empty if that kind has never finished a run.
     */
    Optional<TriggerRequest> findLastFinished(TriggerKind kind);
}
