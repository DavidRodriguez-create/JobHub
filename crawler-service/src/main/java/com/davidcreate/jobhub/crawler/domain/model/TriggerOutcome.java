package com.davidcreate.jobhub.crawler.domain.model;

public enum TriggerOutcome {
    COMPLETED,
    NO_TARGETS,
    CANCELLED,
    INTERRUPTED,
    FAILED;

    public String value() {
        return name().toLowerCase();
    }

    public static TriggerOutcome fromValue(String value) {
        if (value == null) {
            return null;
        }
        return TriggerOutcome.valueOf(value.toUpperCase());
    }
}
