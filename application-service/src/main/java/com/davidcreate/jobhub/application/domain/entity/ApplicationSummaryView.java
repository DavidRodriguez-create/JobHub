package com.davidcreate.jobhub.application.domain.entity;

import java.util.UUID;

/**
 * Compact display summary for one resolvable application (ADR 0014): the company name and
 * job title a notification card needs (CoLogo chip + primary label). Used by the internal
 * batch-resolve endpoint consumed by notification-service's enrich-at-read path. Only
 * resolvable ids produce a view; unresolvable ids are omitted by the caller, never represented
 * as a partially-null view.
 */
public record ApplicationSummaryView(
        UUID applicationId,
        String company,
        String jobTitle,
        String companyLogoUrl) {
}
