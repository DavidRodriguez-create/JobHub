package com.davidcreate.jobhub.job.domain.model;

public enum EmploymentType {
    FULL_TIME("full-time"),
    PART_TIME("part-time"),
    CONTRACT("contract"),
    FREELANCE("freelance"),
    INTERNSHIP("internship");

    private final String value;

    EmploymentType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static EmploymentType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (EmploymentType t : values()) {
            if (t.value.equalsIgnoreCase(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown employment type: " + value);
    }
}
