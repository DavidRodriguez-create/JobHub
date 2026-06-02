package com.davidcreate.jobhub.job.domain.exception;

import java.util.UUID;

public class SavedFilterNotFoundException extends RuntimeException {

    public SavedFilterNotFoundException(UUID id) {
        super("Saved filter with id " + id + " not found");
    }
}
