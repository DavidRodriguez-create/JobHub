package com.davidcreate.jobhub.notification.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class UpcomingNextStep {

    private final UUID userId;
    private final UUID applicationId;
    private final String label;
    private final LocalDate stepDate;
    private final OffsetDateTime reminderAt;
    private final String company;
    private final String status;
}
