package com.davidcreate.jobhub.application.domain.valueobject;

import com.davidcreate.jobhub.application.domain.exception.ValidationException;

public enum ApplicationStatus {
    APPLIED,
    SCREENING,
    INTERVIEWING,
    OFFERED,
    REJECTED,
    ACCEPTED,
    WITHDRAWN,
    GHOSTED;

    public String dbValue() {
        return name().toLowerCase();
    }

    public static ApplicationStatus fromDbValue(String raw) {
        if (raw == null) {
            throw new ValidationException("status is required");
        }
        try {
            return ApplicationStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("unknown status: " + raw);
        }
    }

    public boolean isTerminal() {
        return this == REJECTED || this == ACCEPTED || this == WITHDRAWN || this == GHOSTED;
    }
}
