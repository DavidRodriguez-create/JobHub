package com.davidcreate.jobhub.notification.domain.exception;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {

    private final UUID notificationId;

    public NotificationNotFoundException(UUID notificationId) {
        super("Notification not found: " + notificationId);
        this.notificationId = notificationId;
    }

    public UUID getNotificationId() {
        return notificationId;
    }
}
