package com.davidcreate.jobhub.notification.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.notification.adapter.out.persistence.entity.InterviewReminderSentEntity;
import com.davidcreate.jobhub.notification.domain.model.InterviewReminderSent;
import com.davidcreate.jobhub.notification.domain.model.ReminderOffset;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InterviewReminderSentMapper {

    public InterviewReminderSent toDomain(InterviewReminderSentEntity entity) {
        return InterviewReminderSent.builder()
                .id(entity.id)
                .userId(entity.userId)
                .applicationId(entity.applicationId)
                .reminderOffset(ReminderOffset.valueOf(entity.reminderOffset))
                .nextStepDate(entity.nextStepDate)
                .channels(entity.channels)
                .sentAt(entity.sentAt)
                .build();
    }

    public InterviewReminderSentEntity toEntity(InterviewReminderSent domain) {
        InterviewReminderSentEntity entity = new InterviewReminderSentEntity();
        entity.id = domain.getId();
        entity.userId = domain.getUserId();
        entity.applicationId = domain.getApplicationId();
        entity.reminderOffset = domain.getReminderOffset().name();
        entity.nextStepDate = domain.getNextStepDate();
        entity.channels = domain.getChannels();
        entity.sentAt = domain.getSentAt();
        return entity;
    }
}
