package com.davidcreate.jobhub.crawler.domain.model;

public enum TriggerOrigin {
    SCHEDULED,
    MANUAL;

    public String value() {
        return name().toLowerCase();
    }

    public static TriggerOrigin fromValue(String value) {
        if (value == null) {
            return MANUAL;
        }
        return TriggerOrigin.valueOf(value.toUpperCase());
    }
}
