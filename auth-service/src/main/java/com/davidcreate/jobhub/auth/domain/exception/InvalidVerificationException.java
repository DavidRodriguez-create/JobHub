package com.davidcreate.jobhub.auth.domain.exception;

/**
 * Raised when an email-verification token or destructive-action code is missing,
 * wrong, already used, or expired. Maps to HTTP 400.
 */
public class InvalidVerificationException extends RuntimeException {

    public InvalidVerificationException(String message) {
        super(message);
    }
}
