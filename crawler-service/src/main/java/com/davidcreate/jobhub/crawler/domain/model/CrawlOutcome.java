package com.davidcreate.jobhub.crawler.domain.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Immutable value returned by doCrawl: captures both the raw PullResult and
 * the count of genuinely-new job posts inserted during this source step.
 * Framework-free (no JPA, CDI or JAX-RS annotations).
 */
@Getter
@Builder
public class CrawlOutcome {
    private final PullResult result;
    private final int newPosts;
    private final String companyName;
    private final String sourceType;
    private final int foundPosts;
}
