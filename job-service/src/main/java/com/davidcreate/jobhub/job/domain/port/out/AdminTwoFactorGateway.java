package com.davidcreate.jobhub.job.domain.port.out;

import java.util.UUID;

/**
 * Service-to-service gate on the triggering admin's own two-factor authentication
 * (ADR 0019). Backed by auth-service's {@code /internal/users/{userId}/two-factor}
 * and {@code /internal/two-factor/verify} endpoints.
 */
public interface AdminTwoFactorGateway {

    /**
     * @param userId the admin's own user id (the JWT {@code sub})
     * @return {@code true} when the admin has TOTP two-factor authentication enabled
     */
    boolean isEnabled(UUID userId);

    /**
     * Authorizes the admin to fire a trigger. Returns normally (no exception) both
     * when the admin has no 2FA enabled (the code, if any, is ignored) and when the
     * admin has 2FA enabled and {@code code} is a valid, unused TOTP or backup code.
     *
     * @param userId the admin's own user id (the JWT {@code sub})
     * @param code   the admin's own TOTP or backup code; may be {@code null}
     * @throws com.davidcreate.jobhub.job.domain.exception.VerificationRequiredException
     *         when the admin has 2FA enabled and {@code code} is missing, invalid,
     *         expired, or already used
     * @throws com.davidcreate.jobhub.job.domain.exception.VerificationThrottledException
     *         when auth-service throttles repeated failed attempts for this admin
     */
    void verify(UUID userId, String code);
}
