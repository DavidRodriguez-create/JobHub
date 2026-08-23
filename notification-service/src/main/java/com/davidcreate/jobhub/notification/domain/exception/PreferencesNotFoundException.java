package com.davidcreate.jobhub.notification.domain.exception;

import java.util.UUID;

public class PreferencesNotFoundException extends RuntimeException {

    private final UUID userId;

    public PreferencesNotFoundException(UUID userId) {
        super("Notification preferences not found for user: " + userId);
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
}
