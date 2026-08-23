package com.davidcreate.jobhub.notification.domain.exception;

public class CustomReminderTriggerInPastException extends RuntimeException {

    public CustomReminderTriggerInPastException() {
        super("triggerAtUtc must be strictly in the future");
    }
}
