package com.davidcreate.jobhub.auth.application.port.in;

/**
 * Service-to-service authorization decision: does the supplied code (or the user's lack
 * of 2FA enrolment) authorize the caller to proceed? Used by job-service to gate an admin
 * crawl/enrichment trigger against the admin's own 2FA (ADR 0019). Reuses the existing
 * TOTP + backup-code verification (login-2fa path): a valid TOTP code has no side effect
 * and is reusable; a valid backup code is consumed (single use); repeated failures are
 * throttled.
 */
public interface VerifyTwoFactorForServiceUseCase {

    /**
     * @throws com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException if no user
     *         exists for {@code command.userId()}
     * @throws com.davidcreate.jobhub.auth.domain.exception.TwoFactorVerificationRequiredException
     *         if the user has 2FA enabled and the code is missing, invalid, expired, or an
     *         already-used backup code
     * @throws com.davidcreate.jobhub.auth.domain.exception.TooManyRequestsException if the
     *         user has exceeded the configured max failed-verify attempts
     */
    VerifyTwoFactorOutcome verify(VerifyTwoFactorForServiceCommand command);
}
