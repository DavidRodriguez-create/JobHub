package com.davidcreate.jobhub.job.domain.port.out;

import com.davidcreate.jobhub.job.domain.model.TriggerKind;
import com.davidcreate.jobhub.job.domain.model.TriggerRequest;

import java.util.UUID;

/**
 * Service-to-service gateway onto crawler-service's internal trigger-request write
 * surface (ADR 0033, ticket #583). crawler-service is now the sole writer of
 * {@code crawler.trigger_request}: job-service no longer inserts/updates the row
 * directly, it asks crawler-service to do it and reads the result back.
 */
public interface CrawlerTriggerGateway {

    /**
     * Queues a new {@code queued} row for the given kind. crawler-service is the sole
     * authority on acceptance: it re-checks the active-row rule itself rather than
     * trusting a caller-side pre-check.
     *
     * @throws com.davidcreate.jobhub.job.domain.exception.TriggerInProgressException
     *         when crawler-service rejects the request because a {@code queued} row
     *         of this kind already exists
     * @throws com.davidcreate.jobhub.job.domain.exception.CrawlerUnavailableException
     *         when crawler-service cannot be reached; nothing was started
     */
    TriggerRequest queue(TriggerKind kind, UUID requestedBy);

    /**
     * Cancels the active (queued or running) request for the given kind.
     *
     * @throws com.davidcreate.jobhub.job.domain.exception.NoActiveTriggerException
     *         when crawler-service reports no active (queued or running) request for
     *         this kind
     * @throws com.davidcreate.jobhub.job.domain.exception.CrawlerUnavailableException
     *         when crawler-service cannot be reached; nothing was changed
     */
    TriggerRequest cancel(TriggerKind kind);
}
