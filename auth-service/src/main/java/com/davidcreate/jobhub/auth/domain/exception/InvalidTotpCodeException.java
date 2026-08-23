package com.davidcreate.jobhub.auth.domain.exception;

public class InvalidTotpCodeException extends RuntimeException {

    public InvalidTotpCodeException() {
        super("TOTP code or backup code is missing or invalid");
    }

    public InvalidTotpCodeException(String message) {
        super(message);
    }
}
