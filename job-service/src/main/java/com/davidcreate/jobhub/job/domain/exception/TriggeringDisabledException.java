package com.davidcreate.jobhub.job.domain.exception;

public class TriggeringDisabledException extends RuntimeException {

    public TriggeringDisabledException() {
        super("Admin crawl/enrichment triggering is disabled by deployment config");
    }
}
