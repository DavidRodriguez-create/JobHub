package com.davidcreate.jobhub.notification.domain.exception;

import java.util.UUID;

public class CustomReminderNotScheduledException extends RuntimeException {

    public CustomReminderNotScheduledException(UUID reminderId) {
        super("Custom reminder is not in SCHEDULED status: " + reminderId);
    }
}
