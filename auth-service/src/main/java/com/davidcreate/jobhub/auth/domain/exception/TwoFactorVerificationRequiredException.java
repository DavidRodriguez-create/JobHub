package com.davidcreate.jobhub.auth.domain.exception;

/**
 * Thrown by the service-to-service verify use case (ADR 0019) when the named user has
 * 2FA enabled and the supplied code was missing, invalid, expired, or an already-used
 * backup code. Mapped to HTTP 422 (not 401) so it never collides with the 401 returned
 * for a bad {@code X-Service-Key}.
 */
public class TwoFactorVerificationRequiredException extends RuntimeException {

    public TwoFactorVerificationRequiredException() {
        super("two-factor verification code is required and must be valid");
    }
}
