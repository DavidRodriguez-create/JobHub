package com.davidcreate.jobhub.crawler.domain.port.out;

import com.davidcreate.jobhub.crawler.domain.model.CrawlProgress;

import java.util.UUID;

/**
 * Fire-and-forget writer of live crawl-run progress onto the existing
 * {@code crawler.trigger_request} row (ADR 0029). Kept separate from
 * {@link TriggerRequestQueue} because its contract is different: every
 * method is a no-op when {@code triggerRequestId} is {@code null} (the
 * scheduler path has no trigger request), and no method ever throws --
 * visibility must never break a crawl.
 */
public interface CrawlProgressRecorder {

    /**
     * Marks the given target as currently in flight, right after it is
     * claimed and before the (slow) HTTP fetch starts, so the admin screen
     * can show "crawling X" during the whole time that target takes.
     */
    void markCurrentTarget(UUID triggerRequestId, String companyName, String sourceType);

    /**
     * Records the running totals plus the just-finished target's own
     * found/new pair, and clears the current-target fields (the target that
     * was "current" is now "last").
     */
    void recordTargetCompleted(UUID triggerRequestId, CrawlProgress progress);

    /**
     * Clears the current-target fields without touching the counters. Called
     * once when the batch loop exits, for any reason, so a finished run
     * never claims to still be crawling something.
     */
    void clearCurrentTarget(UUID triggerRequestId);
}
