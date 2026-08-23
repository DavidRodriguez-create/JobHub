package com.davidcreate.jobhub.auth.application.port.in;

import com.davidcreate.jobhub.auth.domain.entity.User;

public interface VerifyEmailUseCase {

    /**
     * Verifies the email address by matching the 6-digit code against the newest
     * unconsumed verify-email code for the given email address. On success, marks
     * the account as email-verified and consumes the code (single use).
     *
     * @return the updated, now-verified User
     * @throws com.davidcreate.jobhub.auth.domain.exception.InvalidVerificationException
     *         when email is unknown, code is wrong, expired, or already consumed
     * @throws com.davidcreate.jobhub.auth.domain.exception.TooManyRequestsException
     *         when repeated failed attempts exceed the configured threshold
     */
    User verify(String email, String code);
}
