package com.davidcreate.jobhub.notification.domain.port.in;

import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;

import java.util.UUID;

public interface UpdatePreferencesUseCase {

    NotificationPreferences updatePreferences(UUID userId,
                                              Boolean weeklyDigestEmail,
                                              Boolean inAppNotificationsEnabled,
                                              Boolean interviewReminders,
                                              Boolean interviewReminderEmail,
                                              Boolean ghostedAlert);
}
