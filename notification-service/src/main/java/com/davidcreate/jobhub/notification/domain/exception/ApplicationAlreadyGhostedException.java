package com.davidcreate.jobhub.notification.domain.exception;

import java.util.UUID;

/**
 * Thrown when application-service returns 409 Conflict for a status update, indicating
 * the application is already in a terminal state (e.g. already ghosted). The
 * ghosted-alert service treats this as a non-fatal, skippable condition.
 */
public class ApplicationAlreadyGhostedException extends RuntimeException {

    private final UUID applicationId;

    public ApplicationAlreadyGhostedException(UUID applicationId) {
        super("Application " + applicationId + " is already in a terminal state (409 from application-service)");
        this.applicationId = applicationId;
    }

    public UUID getApplicationId() {
        return applicationId;
    }
}
