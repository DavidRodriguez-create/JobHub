package com.davidcreate.jobhub.application.domain.entity;

import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Application {

    private final UUID id;
    private final UUID userId;
    private final UUID jobPostSnapshotId;
    private final UUID userJobPostId;
    private final UUID jobPostId;
    private final ApplicationStatus status;
    private final OffsetDateTime appliedAt;
    private final OffsetDateTime endedAt;

    // Detail-view fields
    private final String notes;
    private final String contact;
    private final String portalUrl;

    // Next step
    private final String nextStepLabel;
    private final LocalDate nextStepDate;
    private final OffsetDateTime nextStepReminderAt;

    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public boolean hasNextStep() {
        return nextStepLabel != null || nextStepDate != null || nextStepReminderAt != null;
    }
}
