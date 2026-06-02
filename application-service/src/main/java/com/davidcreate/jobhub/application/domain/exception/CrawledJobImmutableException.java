package com.davidcreate.jobhub.application.domain.exception;

public class CrawledJobImmutableException extends RuntimeException {
    public CrawledJobImmutableException() {
        super("cannot update job details on a crawled-job application — its snapshot is immutable");
    }
}
