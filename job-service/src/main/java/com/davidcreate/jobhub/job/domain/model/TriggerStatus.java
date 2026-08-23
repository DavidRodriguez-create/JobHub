package com.davidcreate.jobhub.job.domain.model;

public enum TriggerStatus {
    QUEUED("queued"),
    RUNNING("running"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    CANCEL_REQUESTED("cancel_requested"),
    CANCELLED("cancelled");

    private final String value;

    TriggerStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static TriggerStatus fromValue(String value) {
        for (TriggerStatus s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown trigger status '" + value + "'");
    }
}
