package com.davidcreate.jobhub.job.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class TriggerRequest {

    private UUID id;
    private TriggerKind kind;
    private TriggerStatus status;
    private UUID requestedBy;
    private OffsetDateTime requestedAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private String resultSummary;
    private String errorReason;
    private TriggerProgress progress;
    private TriggerOrigin origin;
    private TriggerOutcome outcome;
}
