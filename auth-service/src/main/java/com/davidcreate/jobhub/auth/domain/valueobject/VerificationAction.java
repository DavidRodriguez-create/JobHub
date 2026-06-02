package com.davidcreate.jobhub.auth.domain.valueobject;

import com.davidcreate.jobhub.auth.domain.exception.ValidationException;

/**
 * A destructive action that must be confirmed with an emailed verification code.
 */
public enum VerificationAction {
    DELETE_ACCOUNT("delete-account"),
    DELETE_ALL_APPLICATIONS("delete-all-applications");

    private final String value;

    VerificationAction(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static VerificationAction fromValue(String raw) {
        if (raw != null) {
            for (VerificationAction a : values()) {
                if (a.value.equals(raw)) {
                    return a;
                }
            }
        }
        throw new ValidationException("unknown verification action: " + raw);
    }
}
