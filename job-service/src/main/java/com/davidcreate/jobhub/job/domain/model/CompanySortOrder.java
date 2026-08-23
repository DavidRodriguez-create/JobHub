package com.davidcreate.jobhub.job.domain.model;

/**
 * Sort order for the admin company browse endpoint (story #430, ADR 0025 D3).
 * Mirrors {@link JobSortOrder}'s value/fromValue convention.
 */
public enum CompanySortOrder {
    NAME_ASC("name-asc"),
    NAME_DESC("name-desc"),
    UPDATED_DESC("updated-desc"),
    UPDATED_ASC("updated-asc");

    private final String value;

    CompanySortOrder(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static CompanySortOrder fromValue(String value) {
        if (value == null) {
            return NAME_ASC;
        }
        for (CompanySortOrder s : values()) {
            if (s.value.equalsIgnoreCase(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown sort value: " + value);
    }
}
