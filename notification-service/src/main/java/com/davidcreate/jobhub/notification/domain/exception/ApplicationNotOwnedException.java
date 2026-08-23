package com.davidcreate.jobhub.notification.domain.exception;

import java.util.UUID;

public class ApplicationNotOwnedException extends RuntimeException {

    public ApplicationNotOwnedException(UUID applicationId) {
        super("Application not found, or not owned by the authenticated user: " + applicationId);
    }
}
