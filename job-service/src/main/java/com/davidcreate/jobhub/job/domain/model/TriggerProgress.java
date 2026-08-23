package com.davidcreate.jobhub.job.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * Live progress of a crawl run, read-only from job-service's point of view
 * (crawler-service is the sole writer, ADR 0029). Present only on a {@code crawl}
 * {@link TriggerRequest}, and only once the run has reported at least one update;
 * see {@code TriggerRequestMapper} for the null marker ({@code progressUpdatedAt}).
 */
@Getter
@Builder
public class TriggerProgress {

    private Integer targetsVisited;
    private Integer newPosts;
    private String currentCompany;
    private String currentSourceType;
    private String lastCompany;
    private String lastSourceType;
    private Integer lastFoundPosts;
    private Integer lastNewPosts;
    private OffsetDateTime updatedAt;
}
