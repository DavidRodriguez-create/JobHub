package com.davidcreate.jobhub.job.domain.model;

/**
 * Governs the job-search count strategy (ADR 0018 / {@code job.search.count.mode}).
 * {@code EXACT} restores the legacy always-exact behaviour; {@code ESTIMATE} always
 * returns the planner row estimate; {@code HYBRID} (default) returns the exact count
 * at/below {@code job.search.count.exact-threshold} and the estimate above it.
 */
public enum CountMode {
    EXACT("exact"),
    ESTIMATE("estimate"),
    HYBRID("hybrid");

    private final String value;

    CountMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static CountMode fromValue(String value) {
        if (value == null) {
            return HYBRID;
        }
        for (CountMode m : values()) {
            if (m.value.equalsIgnoreCase(value)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown job.search.count.mode value: " + value);
    }
}
