package com.davidcreate.jobhub.application.domain.exception;

import java.util.UUID;

public class UserJobPostNotFoundException extends RuntimeException {
    public UserJobPostNotFoundException(UUID id) {
        super("user-job-post not found: " + id);
    }
}
