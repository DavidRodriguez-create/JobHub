package com.davidcreate.jobhub.application.domain.exception;

/**
 * Raised when the verification code supplied to a destructive endpoint
 * (DELETE /applications) is rejected by auth-service. Maps to HTTP 400.
 */
public class InvalidVerificationException extends RuntimeException {

    public InvalidVerificationException(String message) {
        super(message);
    }
}
