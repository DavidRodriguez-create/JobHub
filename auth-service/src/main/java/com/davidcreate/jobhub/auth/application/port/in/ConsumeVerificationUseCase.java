package com.davidcreate.jobhub.auth.application.port.in;

import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;

import java.util.UUID;

public interface ConsumeVerificationUseCase {

    /**
     * Validate and atomically consume a verification code on behalf of another service
     * that owns the destructive action (e.g. application-service deleting all applications).
     * The code must belong to {@code userId}, match {@code action}, and be unused and unexpired.
     * Throws {@code InvalidVerificationException} otherwise.
     */
    void consume(UUID userId, UUID verificationId, String code, VerificationAction action);
}
