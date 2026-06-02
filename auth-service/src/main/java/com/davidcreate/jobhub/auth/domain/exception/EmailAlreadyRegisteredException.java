package com.davidcreate.jobhub.auth.domain.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException(String email) {
        super("email already registered: " + email);
    }
}
