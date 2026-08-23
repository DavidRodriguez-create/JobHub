package com.davidcreate.jobhub.notification.domain.exception;

public class DigestSendException extends RuntimeException {

    public DigestSendException(String message, Throwable cause) {
        super(message, cause);
    }

    public DigestSendException(String message) {
        super(message);
    }
}
