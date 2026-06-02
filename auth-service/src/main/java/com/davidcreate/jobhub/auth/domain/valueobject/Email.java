package com.davidcreate.jobhub.auth.domain.valueobject;

import com.davidcreate.jobhub.auth.domain.exception.ValidationException;

import java.util.regex.Pattern;

public final class Email {

    private static final Pattern EMAIL_REGEX = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final String value;

    private Email(String value) {
        this.value = value;
    }

    public static Email of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("email must not be blank");
        }
        String normalized = raw.trim().toLowerCase();
        if (!EMAIL_REGEX.matcher(normalized).matches()) {
            throw new ValidationException("email is not a valid address");
        }
        return new Email(normalized);
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
