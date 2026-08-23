package com.davidcreate.jobhub.crawler.domain.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CrawlBatchResult {
    /** Number of sources/targets visited this run (observability; not new posts). */
    private final int crawled;
    /** Cumulative genuinely-new job posts inserted this run. */
    private final int newPosts;
    private final boolean hasMore;
    private final boolean cancelled;
    /** completed / no_targets / cancelled outcome of this batch (story #398, ADR 0032). */
    private final TriggerOutcome outcome;

    /** True only when nothing happened at all (no targets visited and no new posts). */
    public boolean isEmpty() {
        return newPosts == 0 && crawled == 0;
    }
}
