package com.davidcreate.jobhub.crawler.domain.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CrawlBatchResult {
    private final int crawled;
    private final boolean hasMore;

    public boolean isEmpty() {
        return crawled == 0;
    }
}
