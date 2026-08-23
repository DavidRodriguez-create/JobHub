package com.davidcreate.jobhub.crawler.adapter.in.rest.dto;

import com.davidcreate.jobhub.crawler.contract.model.TriggerKind;
import com.davidcreate.jobhub.crawler.contract.model.TriggerOrigin;
import com.davidcreate.jobhub.crawler.contract.model.TriggerRequestResponse;
import com.davidcreate.jobhub.crawler.contract.model.TriggerStatusValue;
import com.davidcreate.jobhub.crawler.domain.model.TriggerRequest;

public final class TriggerRequestResponseMapper {

    private TriggerRequestResponseMapper() {
    }

    public static TriggerRequestResponse toResponse(TriggerRequest domain) {
        return new TriggerRequestResponse()
                .id(domain.getId())
                .kind(TriggerKind.fromValue(domain.getKind().value()))
                .status(TriggerStatusValue.fromValue(domain.getStatus().value()))
                .origin(TriggerOrigin.fromValue(domain.getOrigin().value()))
                .requestedBy(domain.getRequestedBy())
                .requestedAt(domain.getRequestedAt())
                .finishedAt(domain.getFinishedAt())
                .resultSummary(domain.getResultSummary());
    }
}
