package com.davidcreate.jobhub.application.domain.exception;

import java.util.UUID;

public class ApplicationNotFoundException extends RuntimeException {
    public ApplicationNotFoundException(UUID id) {
        super("application not found: " + id);
    }
}
