package com.davidcreate.jobhub.crawler.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.PullTargetEntity;
import com.davidcreate.jobhub.crawler.domain.model.PullTarget;
import com.davidcreate.jobhub.crawler.domain.model.PullTargetStatus;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PullTargetMapper {

    public PullTarget toDomain(PullTargetEntity entity) {
        return PullTarget.builder()
                .id(entity.id)
                .sourceType(entity.sourceType)
                .companyName(entity.companyName)
                .companyLogoUrl(entity.companyLogoUrl)
                .token(entity.token)
                .scraperConfig(entity.scraperConfig)
                .pullPriority(entity.pullPriority)
                .status(PullTargetStatus.valueOf(entity.status.toUpperCase()))
                .statusReason(entity.statusReason)
                .statusChangedAt(entity.statusChangedAt)
                .lockedBy(entity.lockedBy)
                .leaseExpiresAt(entity.leaseExpiresAt)
                .lastSuccessfulPull(entity.lastSuccessfulPull)
                .lastPullAttempt(entity.lastPullAttempt)
                .consecutiveFailures(entity.consecutiveFailures)
                .createdAt(entity.createdAt)
                .updatedAt(entity.updatedAt)
                .build();
    }

    public PullTargetEntity toEntity(PullTarget domain) {
        PullTargetEntity entity = new PullTargetEntity();
        entity.id = domain.getId();
        entity.sourceType = domain.getSourceType();
        entity.companyName = domain.getCompanyName();
        entity.companyLogoUrl = domain.getCompanyLogoUrl();
        entity.token = domain.getToken();
        entity.scraperConfig = domain.getScraperConfig();
        entity.pullPriority = domain.getPullPriority();
        entity.nextPullAfter = domain.getNextPullAfter();
        entity.status = domain.getStatus().name().toLowerCase();
        entity.statusReason = domain.getStatusReason();
        entity.statusChangedAt = domain.getStatusChangedAt();
        entity.lockedBy = domain.getLockedBy();
        entity.leaseExpiresAt = domain.getLeaseExpiresAt();
        entity.lastSuccessfulPull = domain.getLastSuccessfulPull();
        entity.lastPullAttempt = domain.getLastPullAttempt();
        entity.consecutiveFailures = domain.getConsecutiveFailures();
        entity.createdAt = domain.getCreatedAt();
        entity.updatedAt = domain.getUpdatedAt();
        return entity;
    }

    public void updateEntity(PullTargetEntity entity, PullTarget domain) {
        entity.status = domain.getStatus().name().toLowerCase();
        entity.statusReason = domain.getStatusReason();
        entity.statusChangedAt = domain.getStatusChangedAt();
        entity.lockedBy = domain.getLockedBy();
        entity.leaseExpiresAt = domain.getLeaseExpiresAt();
        entity.lastSuccessfulPull = domain.getLastSuccessfulPull();
        entity.lastPullAttempt = domain.getLastPullAttempt();
        entity.consecutiveFailures = domain.getConsecutiveFailures();
        entity.nextPullAfter = domain.getNextPullAfter();
        entity.updatedAt = domain.getUpdatedAt();
    }
}