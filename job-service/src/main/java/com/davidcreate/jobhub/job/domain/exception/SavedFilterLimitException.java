package com.davidcreate.jobhub.job.domain.exception;

public class SavedFilterLimitException extends RuntimeException {

    public SavedFilterLimitException(int max) {
        super("Saved filter limit reached (max " + max + "); delete one before creating another");
    }
}
