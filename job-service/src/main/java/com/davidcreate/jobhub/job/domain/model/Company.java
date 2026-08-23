package com.davidcreate.jobhub.job.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A company, resolved from a stored {@code crawler.company} row (story #428, ADR 0023),
 * or a transitional fallback carrying only {@code name}/{@code logoUrl} from the crawl-time
 * {@code pull_target} columns while the target's {@code company_id} is still unresolved.
 *
 * <p>{@code manuallyEdited} is a boxed {@link Boolean}, not a primitive: {@code null} is the
 * signal that the company itself is unresolved (the fallback path), distinct from a resolved
 * company that has never been manually edited ({@code false}).
 */
@Getter
@Builder
public class Company {

    private final UUID id;
    private final String slug;
    private final String name;
    private final String website;
    private final String industry;
    private final String size;
    private final String headquarters;
    private final String description;
    private final String logoUrl;
    private final List<String> tags;
    private final Boolean manuallyEdited;
    private final OffsetDateTime updatedAt;
}
