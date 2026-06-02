package com.davidcreate.jobhub.application.domain.exception;

import java.util.UUID;

public class JobPostNotFoundException extends RuntimeException {
    public JobPostNotFoundException(UUID jobPostId) {
        super("job post not found: " + jobPostId);
    }
}
