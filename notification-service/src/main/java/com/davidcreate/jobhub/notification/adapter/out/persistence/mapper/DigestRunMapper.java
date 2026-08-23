package com.davidcreate.jobhub.notification.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.notification.adapter.out.persistence.entity.DigestRunEntity;
import com.davidcreate.jobhub.notification.domain.model.DigestRun;
import com.davidcreate.jobhub.notification.domain.model.DigestRunStatus;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DigestRunMapper {

    public DigestRun toDomain(DigestRunEntity entity) {
        return DigestRun.builder()
                .id(entity.id)
                .userId(entity.userId)
                .sentAt(entity.sentAt)
                .jobCount(entity.jobCount)
                .status(toDomainStatus(entity.status))
                .errorMessage(entity.errorMessage)
                .build();
    }

    public DigestRunEntity toEntity(DigestRun domain) {
        DigestRunEntity entity = new DigestRunEntity();
        entity.id = domain.getId();
        entity.userId = domain.getUserId();
        entity.sentAt = domain.getSentAt();
        entity.jobCount = domain.getJobCount();
        entity.status = toEntityStatus(domain.getStatus());
        entity.errorMessage = domain.getErrorMessage();
        return entity;
    }

    private DigestRunStatus toDomainStatus(String status) {
        return DigestRunStatus.valueOf(status.toUpperCase());
    }

    private String toEntityStatus(DigestRunStatus status) {
        return status.name().toLowerCase();
    }
}
