package com.davidcreate.jobhub.notification.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class DigestRun {

    private final UUID id;
    private final UUID userId;
    private final Instant sentAt;
    private final int jobCount;
    private final DigestRunStatus status;
    private final String errorMessage;
}
