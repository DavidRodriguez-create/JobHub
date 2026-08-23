package com.davidcreate.jobhub.job.domain.exception;

public class VerificationRequiredException extends RuntimeException {

    public VerificationRequiredException(String message) {
        super(message);
    }
}
