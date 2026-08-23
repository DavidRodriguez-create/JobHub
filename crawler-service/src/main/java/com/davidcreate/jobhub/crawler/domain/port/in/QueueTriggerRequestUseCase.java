package com.davidcreate.jobhub.crawler.domain.port.in;

import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.model.TriggerOrigin;
import com.davidcreate.jobhub.crawler.domain.model.TriggerRequest;

import java.util.UUID;

/**
 * Story #582 (ADR 0033): crawler-service is the sole writer of
 * {@code crawler.trigger_request}. Queues a new {@code queued} row on behalf of a
 * calling service (job-service, via the internal {@code /internal/trigger-requests}
 * endpoint).
 */
public interface QueueTriggerRequestUseCase {

    /**
     * @throws com.davidcreate.jobhub.crawler.domain.exception.ConflictException if a
     *         {@code queued} row of this kind already exists.
     */
    TriggerRequest queue(TriggerKind kind, TriggerOrigin origin, UUID requestedBy);
}
