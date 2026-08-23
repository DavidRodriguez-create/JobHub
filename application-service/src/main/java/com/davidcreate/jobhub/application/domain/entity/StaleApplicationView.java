package com.davidcreate.jobhub.application.domain.entity;

import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;

import java.util.UUID;

/**
 * A lightweight view of a non-terminal application that has exceeded the inactivity threshold.
 * Used by the ghosted-alert scheduler via the internal stale-applications endpoint.
 */
public record StaleApplicationView(
        UUID id,
        UUID userId,
        String jobTitle,
        String company,
        ApplicationStatus currentStatus,
        int daysSinceLastActivity) {
}
