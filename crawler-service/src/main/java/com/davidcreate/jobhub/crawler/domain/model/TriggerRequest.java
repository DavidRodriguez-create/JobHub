package com.davidcreate.jobhub.crawler.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class TriggerRequest {

    private final UUID id;
    private final TriggerKind kind;
    private final TriggerStatus status;
    private final UUID requestedBy;
    private final OffsetDateTime requestedAt;
    private final OffsetDateTime startedAt;
    private final OffsetDateTime finishedAt;
    private final String resultSummary;
    private final String errorReason;
    private final TriggerOrigin origin;
    private final TriggerOutcome outcome;
}
