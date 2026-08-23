package com.davidcreate.jobhub.crawler.domain.port.in;

import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.model.TriggerRequest;

/**
 * Story #582 (ADR 0033): crawler-service is the sole writer of
 * {@code crawler.trigger_request}. Cancels the currently active request of a kind on
 * behalf of a calling service.
 */
public interface CancelTriggerRequestUseCase {

    /**
     * @throws com.davidcreate.jobhub.crawler.domain.exception.ResourceNotFoundException
     *         if no active ({@code queued} or {@code running}) request of this kind
     *         exists.
     */
    TriggerRequest cancel(TriggerKind kind);
}
