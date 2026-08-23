package com.davidcreate.jobhub.job.domain.model;

/**
 * Who started a trigger run (story #398, ADR 0032). {@code MANUAL} is an
 * admin-triggered request recorded by job-service; {@code SCHEDULED} is
 * crawler-service's own periodic pass. Rows created before ADR 0032 read back
 * as {@code MANUAL} (the {@code crawler.trigger_request.origin} column
 * defaults to {@code manual}; job-service never writes this column, see
 * {@link TriggerRequestMapper}).
 */
public enum TriggerOrigin {
    MANUAL("manual"),
    SCHEDULED("scheduled");

    private final String value;

    TriggerOrigin(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static TriggerOrigin fromValue(String value) {
        for (TriggerOrigin o : values()) {
            if (o.value.equals(value)) {
                return o;
            }
        }
        throw new IllegalArgumentException("Unknown trigger origin '" + value + "'");
    }
}
