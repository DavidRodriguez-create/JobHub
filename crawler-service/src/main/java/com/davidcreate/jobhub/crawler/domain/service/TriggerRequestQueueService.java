package com.davidcreate.jobhub.crawler.domain.service;

import com.davidcreate.jobhub.crawler.domain.exception.ResourceNotFoundException;
import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.model.TriggerOrigin;
import com.davidcreate.jobhub.crawler.domain.model.TriggerRequest;
import com.davidcreate.jobhub.crawler.domain.port.in.CancelTriggerRequestUseCase;
import com.davidcreate.jobhub.crawler.domain.port.in.QueueTriggerRequestUseCase;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Story #582 (ADR 0033): crawler-service's own authority on {@code crawler.trigger_request}
 * writes. The "one active row per kind" rule is enforced by the persistence adapter against
 * the partial unique index ({@code db/init/060}), not re-checked here: this service simply
 * delegates and lets the port surface {@link com.davidcreate.jobhub.crawler.domain.exception.ConflictException}
 * on a violation.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class TriggerRequestQueueService implements QueueTriggerRequestUseCase, CancelTriggerRequestUseCase {

    private final TriggerRequestQueue triggerRequestQueue;

    @Override
    public TriggerRequest queue(TriggerKind kind, TriggerOrigin origin, UUID requestedBy) {
        TriggerOrigin effectiveOrigin = origin != null ? origin : TriggerOrigin.MANUAL;
        return triggerRequestQueue.enqueue(kind, effectiveOrigin, requestedBy);
    }

    @Override
    public TriggerRequest cancel(TriggerKind kind) {
        return triggerRequestQueue.cancelActive(kind)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active " + kind.value() + " request to cancel"));
    }
}
