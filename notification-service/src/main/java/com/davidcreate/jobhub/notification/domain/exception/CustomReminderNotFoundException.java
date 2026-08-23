package com.davidcreate.jobhub.notification.domain.exception;

import java.util.UUID;

public class CustomReminderNotFoundException extends RuntimeException {

    public CustomReminderNotFoundException(UUID reminderId) {
        super("Custom reminder not found: " + reminderId);
    }
}
