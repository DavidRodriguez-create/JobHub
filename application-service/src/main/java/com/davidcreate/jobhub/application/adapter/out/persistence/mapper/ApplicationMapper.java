package com.davidcreate.jobhub.application.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.application.adapter.out.persistence.entity.ApplicationEntity;
import com.davidcreate.jobhub.application.domain.entity.Application;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ApplicationMapper {

    public Application toDomain(ApplicationEntity e) {
        return Application.builder()
                .id(e.id)
                .userId(e.userId)
                .jobPostSnapshotId(e.jobPostSnapshotId)
                .userJobPostId(e.userJobPostId)
                .jobPostId(e.jobPostId)
                .status(e.status)
                .appliedAt(e.appliedAt)
                .endedAt(e.endedAt)
                .notes(e.notes)
                .contact(e.contact)
                .portalUrl(e.portalUrl)
                .nextStepLabel(e.nextStepLabel)
                .nextStepDate(e.nextStepDate)
                .nextStepReminderAt(e.nextStepReminderAt)
                .createdAt(e.createdAt)
                .updatedAt(e.updatedAt)
                .build();
    }

    public ApplicationEntity toEntity(Application a) {
        ApplicationEntity e = new ApplicationEntity();
        e.id = a.getId();
        e.userId = a.getUserId();
        e.jobPostSnapshotId = a.getJobPostSnapshotId();
        e.userJobPostId = a.getUserJobPostId();
        e.jobPostId = a.getJobPostId();
        e.status = a.getStatus();
        e.appliedAt = a.getAppliedAt();
        e.endedAt = a.getEndedAt();
        e.notes = a.getNotes();
        e.contact = a.getContact();
        e.portalUrl = a.getPortalUrl();
        e.nextStepLabel = a.getNextStepLabel();
        e.nextStepDate = a.getNextStepDate();
        e.nextStepReminderAt = a.getNextStepReminderAt();
        e.createdAt = a.getCreatedAt();
        e.updatedAt = a.getUpdatedAt();
        return e;
    }

    public void updateEntity(ApplicationEntity e, Application a) {
        e.status = a.getStatus();
        e.appliedAt = a.getAppliedAt();
        e.endedAt = a.getEndedAt();
        e.notes = a.getNotes();
        e.contact = a.getContact();
        e.portalUrl = a.getPortalUrl();
        e.nextStepLabel = a.getNextStepLabel();
        e.nextStepDate = a.getNextStepDate();
        e.nextStepReminderAt = a.getNextStepReminderAt();
    }
}
