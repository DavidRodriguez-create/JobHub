package com.davidcreate.jobhub.job.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * A {@code crawler.pull_target} row whose {@code company_id} is still {@code NULL}
 * (story #428, ADR 0023 D5): the raw crawl-time {@code company_name} the resolver slugs
 * to find or create the target's {@link Company}.
 */
@Getter
@Builder
public class UnresolvedTarget {

    private final UUID id;
    private final String companyName;
}
