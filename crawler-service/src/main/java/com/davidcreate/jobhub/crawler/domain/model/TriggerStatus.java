package com.davidcreate.jobhub.crawler.domain.model;

public enum TriggerStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCEL_REQUESTED,
    CANCELLED;

    public String value() {
        return name().toLowerCase();
    }

    public static TriggerStatus fromValue(String value) {
        return TriggerStatus.valueOf(value.toUpperCase());
    }
}
