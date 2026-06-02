package com.davidcreate.jobhub.auth.domain.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("invalid email or password");
    }
}
