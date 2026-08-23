package com.davidcreate.jobhub.notification.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class NotificationPreferences {

    private final UUID id;
    private final UUID userId;
    private final boolean weeklyDigestEmail;
    private final boolean inAppNotificationsEnabled;
    private final boolean interviewReminders;
    private final boolean interviewReminderEmail;
    private final boolean ghostedAlert;
}
