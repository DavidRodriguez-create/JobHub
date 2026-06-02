package com.davidcreate.jobhub.job.domain.model;

public enum JobSortOrder {
    NEWEST("newest"),
    OLDEST("oldest"),
    SALARY_DESC("salary-desc"),
    SALARY_ASC("salary-asc");

    private final String value;

    JobSortOrder(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static JobSortOrder fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (JobSortOrder s : values()) {
            if (s.value.equalsIgnoreCase(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown sort value: " + value);
    }
}
