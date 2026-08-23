package com.davidcreate.jobhub.application.domain.entity;

import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One upcoming next-step occurrence for a single user's application, returned by the
 * internal {@code GET /internal/applications/upcoming-next-steps} endpoint (ADR 0009).
 * Carries everything notification-service needs to build interview reminder content
 * (label, date, company) and to key idempotency (userId + applicationId).
 */
@Getter
@Builder
@AllArgsConstructor
public class UpcomingNextStep {

    private final UUID userId;
    private final UUID applicationId;
    private final String nextStepLabel;
    private final LocalDate nextStepDate;
    private final OffsetDateTime nextStepReminderAt;
    private final String companyName;
    private final ApplicationStatus status;
}
