package com.davidcreate.jobhub.notification.domain.exception;

public class CustomReminderInvalidChannelsException extends RuntimeException {

    public CustomReminderInvalidChannelsException() {
        super("channels must contain at least one entry");
    }
}
