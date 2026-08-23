package com.davidcreate.jobhub.auth.domain.exception;

public class InvalidServiceKeyException extends RuntimeException {
    public InvalidServiceKeyException() {
        super("missing or invalid X-Service-Key");
    }
}
