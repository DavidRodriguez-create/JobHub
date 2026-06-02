package com.davidcreate.jobhub.application.domain.valueobject;

import java.time.OffsetDateTime;

/**
 * A single status-change event in an application's history. The full ordered list
 * (oldest first) is returned only on the single-application detail view.
 */
public record TimelineEntry(ApplicationStatus status, OffsetDateTime occurredAt) {
}
