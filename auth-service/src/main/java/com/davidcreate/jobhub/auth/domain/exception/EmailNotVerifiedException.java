package com.davidcreate.jobhub.auth.domain.exception;

/**
 * Thrown by LoginService when the user's password is correct but the email address
 * has not yet been verified. Mapped to HTTP 403 by EmailNotVerifiedExceptionMapper.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Email address has not been verified. Please verify your email before logging in.");
    }
}
