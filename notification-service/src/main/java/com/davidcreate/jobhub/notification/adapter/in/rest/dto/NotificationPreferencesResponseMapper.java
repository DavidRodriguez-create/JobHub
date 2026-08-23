package com.davidcreate.jobhub.notification.adapter.in.rest.dto;

import com.davidcreate.jobhub.notification.contract.model.NotificationPreferencesResponse;
import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NotificationPreferencesResponseMapper {

    public NotificationPreferencesResponse toResponse(NotificationPreferences domain) {
        return new NotificationPreferencesResponse()
                .weeklyDigestEmail(domain.isWeeklyDigestEmail())
                .inAppNotificationsEnabled(domain.isInAppNotificationsEnabled())
                .interviewReminders(domain.isInterviewReminders())
                .interviewReminderEmail(domain.isInterviewReminderEmail())
                .ghostedAlert(domain.isGhostedAlert());
    }
}
