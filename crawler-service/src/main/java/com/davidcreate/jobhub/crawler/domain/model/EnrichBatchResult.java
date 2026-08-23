package com.davidcreate.jobhub.crawler.domain.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EnrichBatchResult {
    private final int attempted;
    private final int enriched;
    private final boolean cancelled;
}
