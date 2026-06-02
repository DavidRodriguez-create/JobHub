package com.davidcreate.jobhub.crawler.domain.exception;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}