package com.davidcreate.jobhub.crawler.domain.port.in;

import java.util.UUID;

import com.davidcreate.jobhub.crawler.domain.model.CrawlBatchResult;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;

public interface CrawlUseCase {
    void crawl(UUID targetIdUuid);

    /**
     * Convenience overload: crawl until at least {@code minNewPosts} genuinely-new
     * job posts are produced, or all sources are exhausted, or the safety cap on
     * targets visited is reached. No cancellation check.
     *
     * @param minNewPosts the new-post target (must be >= 1)
     */
    CrawlBatchResult crawlBatch(int minNewPosts);

    /**
     * Crawl until cumulative new posts >= {@code minNewPosts}, OR targets visited
     * >= max-targets-per-run, OR no source is available, OR (when non-null)
     * {@code triggerRequestId} has been marked cancel-requested.
     *
     * @param minNewPosts      the new-post target (must be >= 1)
     * @param triggerRequestId the admin-triggered request driving this batch, or
     *                         {@code null} for cron-scheduled runs (no cancel check)
     */
    CrawlBatchResult crawlBatch(int minNewPosts, UUID triggerRequestId);

    /**
     * Same as {@link #crawlBatch(int, UUID)}, additionally checking {@code shutdownSignal}
     * at each item boundary (ADR 0032, story #398): once shutdown has begun, the current
     * item finishes but no new item starts.
     *
     * @param shutdownSignal the shared shutdown signal driving this batch
     */
    CrawlBatchResult crawlBatch(int minNewPosts, UUID triggerRequestId, ShutdownSignal shutdownSignal);
}
