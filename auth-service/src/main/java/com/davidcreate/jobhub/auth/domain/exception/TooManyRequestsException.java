package com.davidcreate.jobhub.auth.domain.exception;

/**
 * Thrown when a rate-limit threshold is exceeded (verify-email repeated failures,
 * or resend-verification burst). Mapped to HTTP 429 by TooManyRequestsExceptionMapper.
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
