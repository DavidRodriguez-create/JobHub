package com.davidcreate.jobhub.notification.domain.model;

public enum NotificationType {
    INTERVIEW_REMINDER,
    GHOSTED_ALERT,
    APPLICATION_UPDATE,
    SYSTEM,
    CUSTOM_REMINDER,
    SECURITY_RECOMMENDATION;

    public NotificationCategory category() {
        return switch (this) {
            case INTERVIEW_REMINDER, GHOSTED_ALERT, APPLICATION_UPDATE, CUSTOM_REMINDER -> NotificationCategory.APPLICATION;
            case SECURITY_RECOMMENDATION, SYSTEM -> NotificationCategory.ACCOUNT;
        };
    }
}
