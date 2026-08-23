package com.davidcreate.jobhub.job.domain.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TriggerStatusOverview {

    private boolean triggerEnabled;
    private boolean twoFactorRequired;
    private TriggerRequest crawl;
    private TriggerRequest enrichment;
    private TriggerRequest lastCrawlRun;
    private TriggerRequest lastEnrichmentRun;
}
