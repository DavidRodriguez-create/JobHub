package com.davidcreate.jobhub.job.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class TriggerCommand {

    private String kind;
    private String code;
    private UUID requestedBy;
}
