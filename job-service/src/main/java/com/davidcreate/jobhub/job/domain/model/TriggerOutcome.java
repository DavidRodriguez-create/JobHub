package com.davidcreate.jobhub.job.domain.model;

/**
 * Machine-readable end state of a finished trigger run (story #398, ADR 0032).
 * {@code null} while {@code queued}/{@code running} and for runs predating
 * ADR 0032, since job-service never writes {@code crawler.trigger_request.outcome}
 * (see {@link TriggerRequestMapper}).
 */
public enum TriggerOutcome {
    COMPLETED("completed"),
    NO_TARGETS("no_targets"),
    CANCELLED("cancelled"),
    INTERRUPTED("interrupted"),
    FAILED("failed");

    private final String value;

    TriggerOutcome(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static TriggerOutcome fromValue(String value) {
        for (TriggerOutcome o : values()) {
            if (o.value.equals(value)) {
                return o;
            }
        }
        throw new IllegalArgumentException("Unknown trigger outcome '" + value + "'");
    }
}
