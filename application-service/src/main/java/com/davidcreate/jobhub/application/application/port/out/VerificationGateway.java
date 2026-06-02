package com.davidcreate.jobhub.application.application.port.out;

import java.util.UUID;

/**
 * Outbound port to auth-service for the two-factor verification of destructive actions.
 * auth-service owns the codes; this gateway validates and consumes one on the caller's behalf.
 */
public interface VerificationGateway {

    /**
     * Validate and consume a {@code delete-all-applications} verification code.
     * Throws {@link com.davidcreate.jobhub.application.domain.exception.InvalidVerificationException}
     * if auth-service rejects the code (invalid, expired, already used, or wrong action).
     *
     * @param bearerToken the caller's raw JWT, forwarded so auth-service identifies the user
     */
    void consumeDeleteAllApplications(String bearerToken, UUID verificationId, String code);
}
