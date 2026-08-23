package com.davidcreate.jobhub.crawler.domain.port.in;

import java.util.UUID;

import com.davidcreate.jobhub.crawler.domain.model.EnrichBatchResult;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;

public interface EnrichJobsUseCase {

    /**
     * Enrich up to {@code limit} job posts still awaiting enrichment.
     *
     * @return the number successfully enriched in this pass
     */
    int enrichPending(int limit);

    /**
     * Enrich up to {@code limit} job posts still awaiting enrichment, checking
     * after each one whether the given {@code triggerRequestId} has been marked
     * {@code cancel_requested}. If cancellation is detected, the loop stops early
     * and the returned result has {@code cancelled = true}.
     *
     * @param triggerRequestId the admin-triggered request driving this batch, or
     *                          {@code null} for cron-scheduled runs (no cancellation
     *                          check is performed when {@code null})
     */
    EnrichBatchResult enrichPending(int limit, UUID triggerRequestId);

    /**
     * Same as {@link #enrichPending(int, UUID)}, additionally checking
     * {@code shutdownSignal} before each posting (ADR 0032, story #398): once shutdown
     * has begun, the posting in flight finishes but no new one starts.
     *
     * @param shutdownSignal the shared shutdown signal driving this batch
     */
    EnrichBatchResult enrichPending(int limit, UUID triggerRequestId, ShutdownSignal shutdownSignal);
}
