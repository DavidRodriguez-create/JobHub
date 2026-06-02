package com.davidcreate.jobhub.job.domain.exception;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(String message) {
        super(message);
    }
}
