package com.davidcreate.jobhub.notification.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.notification.adapter.out.persistence.entity.NotificationPreferencesEntity;
import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NotificationPreferencesMapper {

    public NotificationPreferences toDomain(NotificationPreferencesEntity entity) {
        return NotificationPreferences.builder()
                .id(entity.id)
                .userId(entity.userId)
                .weeklyDigestEmail(entity.weeklyDigestEmail)
                .inAppNotificationsEnabled(entity.inAppNotificationsEnabled)
                .interviewReminders(entity.interviewReminders)
                .interviewReminderEmail(entity.interviewReminderEmail)
                .ghostedAlert(entity.ghostedAlert)
                .build();
    }

    public NotificationPreferencesEntity toEntity(NotificationPreferences domain) {
        NotificationPreferencesEntity entity = new NotificationPreferencesEntity();
        entity.id = domain.getId();
        entity.userId = domain.getUserId();
        entity.weeklyDigestEmail = domain.isWeeklyDigestEmail();
        entity.inAppNotificationsEnabled = domain.isInAppNotificationsEnabled();
        entity.interviewReminders = domain.isInterviewReminders();
        entity.interviewReminderEmail = domain.isInterviewReminderEmail();
        entity.ghostedAlert = domain.isGhostedAlert();
        return entity;
    }
}
