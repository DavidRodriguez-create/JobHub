package com.davidcreate.jobhub.crawler.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.TriggerRequestEntity;
import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.model.TriggerOrigin;
import com.davidcreate.jobhub.crawler.domain.model.TriggerOutcome;
import com.davidcreate.jobhub.crawler.domain.model.TriggerRequest;
import com.davidcreate.jobhub.crawler.domain.model.TriggerStatus;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TriggerRequestMapper {

    public TriggerRequest toDomain(TriggerRequestEntity entity) {
        return TriggerRequest.builder()
                .id(entity.id)
                .kind(TriggerKind.fromValue(entity.kind))
                .status(TriggerStatus.fromValue(entity.status))
                .requestedBy(entity.requestedBy)
                .requestedAt(entity.requestedAt)
                .startedAt(entity.startedAt)
                .finishedAt(entity.finishedAt)
                .resultSummary(entity.resultSummary)
                .errorReason(entity.errorReason)
                .origin(TriggerOrigin.fromValue(entity.origin))
                .outcome(TriggerOutcome.fromValue(entity.outcome))
                .build();
    }
}
