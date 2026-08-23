package com.davidcreate.jobhub.notification.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.net.URI;
import java.util.UUID;

/**
 * Compact display summary for one application, resolved by id via
 * {@link com.davidcreate.jobhub.notification.domain.port.out.ApplicationSummaryGateway}
 * for the enrich-at-read path (ADR 0014). Carries the company name, job title, and
 * company logo URL a notification card needs (ADR 0015).
 */
@Getter
@Builder
public class ApplicationSummary {

    private final UUID applicationId;
    private final String company;
    private final String jobTitle;

    /**
     * Real company logo URL frozen on the crawled-job snapshot at apply time
     * (ADR 0015). Null for manual-entry applications, for crawled jobs whose source
     * post had no logo, and for applications applied before this field was captured.
     * Not persisted: populated by the gateway adapter from the upstream response.
     */
    private final URI companyLogoUrl;
}
