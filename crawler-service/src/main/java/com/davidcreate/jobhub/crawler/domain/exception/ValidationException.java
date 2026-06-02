package com.davidcreate.jobhub.crawler.domain.exception;

public class ValidationException extends IllegalArgumentException {

    public ValidationException(String message) {
        super(message);
    }
}
