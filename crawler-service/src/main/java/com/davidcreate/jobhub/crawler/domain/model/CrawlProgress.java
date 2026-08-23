package com.davidcreate.jobhub.crawler.domain.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Immutable snapshot of a batch's running progress after one target has just
 * completed (ADR 0029): the accumulated totals for the run so far, plus the
 * just-finished target's own found/new pair. Framework-free (no JPA, CDI or
 * JAX-RS annotations).
 */
@Getter
@Builder
public class CrawlProgress {
    private final int targetsVisited;
    private final int newPosts;
    private final String lastCompanyName;
    private final String lastSourceType;
    private final int lastFoundPosts;
    private final int lastNewPosts;
}
