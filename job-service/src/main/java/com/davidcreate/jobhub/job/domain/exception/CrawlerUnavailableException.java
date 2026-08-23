package com.davidcreate.jobhub.job.domain.exception;

/**
 * crawler-service could not be reached (connection refused, timed out, or any other
 * transport-level failure) while queueing or cancelling a trigger request (ADR 0033).
 * Never retried: job-service surfaces this to the admin as 503, who retries the click.
 */
public class CrawlerUnavailableException extends RuntimeException {

    public CrawlerUnavailableException(String message) {
        super(message);
    }
}
