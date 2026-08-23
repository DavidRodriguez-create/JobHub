package com.davidcreate.jobhub.auth.domain.exception;

public class OAuthStateMismatchException extends RuntimeException {
    public OAuthStateMismatchException() {
        super("oauth state is missing, expired, or does not match");
    }
}
