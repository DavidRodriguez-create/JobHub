package com.davidcreate.jobhub.job.adapter.in.rest.dto;

import com.davidcreate.jobhub.job.contract.model.TriggerKind;
import com.davidcreate.jobhub.job.contract.model.TriggerResponse;
import com.davidcreate.jobhub.job.contract.model.TriggerStatusValue;
import com.davidcreate.jobhub.job.domain.model.TriggerRequest;

public final class TriggerResponseMapper {

    private TriggerResponseMapper() {
    }

    public static TriggerResponse toResponse(TriggerRequest domain) {
        return new TriggerResponse()
                .id(domain.getId())
                .kind(TriggerKind.fromValue(domain.getKind().value()))
                .status(TriggerStatusValue.fromValue(domain.getStatus().value()))
                .requestedAt(domain.getRequestedAt());
    }
}
