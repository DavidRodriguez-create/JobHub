package com.davidcreate.jobhub.auth.domain.valueobject;

import com.davidcreate.jobhub.auth.domain.exception.ValidationException;

public final class Password {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 100;

    private final String raw;

    private Password(String raw) {
        this.raw = raw;
    }

    public static Password of(String raw) {
        if (raw == null || raw.length() < MIN_LENGTH) {
            throw new ValidationException("password must be at least " + MIN_LENGTH + " characters");
        }
        if (raw.length() > MAX_LENGTH) {
            throw new ValidationException("password must be at most " + MAX_LENGTH + " characters");
        }
        return new Password(raw);
    }

    public String raw() {
        return raw;
    }
}
