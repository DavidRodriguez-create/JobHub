
package com.davidcreate.jobhub.crawler.domain.port.in;

import java.util.UUID;

import com.davidcreate.jobhub.crawler.domain.model.CrawlBatchResult;

public interface CrawlUseCase {
    void crawl(UUID targetIdUuid);

    CrawlBatchResult crawlBatch(int limit);
}