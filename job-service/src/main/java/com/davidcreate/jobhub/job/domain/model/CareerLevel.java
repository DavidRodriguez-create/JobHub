package com.davidcreate.jobhub.job.domain.model;

public enum CareerLevel {
    INTERNSHIP("internship"),
    JUNIOR("junior"),
    MID("mid"),
    SENIOR("senior"),
    LEAD("lead"),
    PRINCIPAL("principal"),
    MANAGER("manager"),
    DIRECTOR("director");

    private final String value;

    CareerLevel(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static CareerLevel fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (CareerLevel l : values()) {
            if (l.value.equalsIgnoreCase(value)) {
                return l;
            }
        }
        throw new IllegalArgumentException("Unknown career level: " + value);
    }
}
