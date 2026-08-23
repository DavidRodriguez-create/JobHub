package com.davidcreate.jobhub.job.domain.exception;

public class VerificationThrottledException extends RuntimeException {

    public VerificationThrottledException(String message) {
        super(message);
    }
}
