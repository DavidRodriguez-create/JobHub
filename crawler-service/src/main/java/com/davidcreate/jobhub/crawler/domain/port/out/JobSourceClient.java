package com.davidcreate.jobhub.crawler.domain.port.out;

import com.davidcreate.jobhub.crawler.domain.model.PullResult;
import com.davidcreate.jobhub.crawler.domain.model.PullTarget;

public interface JobSourceClient {
    boolean supports(String sourceType);

    PullResult crawl(PullTarget target);
}
