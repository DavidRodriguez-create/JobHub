package com.davidcreate.jobhub.job.domain.model;

import java.time.Duration;

public enum PostedWithin {
    TODAY("today", Duration.ofDays(1)),
    THREE_DAYS("3d", Duration.ofDays(3)),
    WEEK("week", Duration.ofDays(7)),
    MONTH("month", Duration.ofDays(30));

    private final String value;
    private final Duration window;

    PostedWithin(String value, Duration window) {
        this.value = value;
        this.window = window;
    }

    public String value() {
        return value;
    }

    public Duration window() {
        return window;
    }

    public static PostedWithin fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (PostedWithin p : values()) {
            if (p.value.equalsIgnoreCase(value)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown postedWithin value: " + value);
    }
}
