package com.davidcreate.jobhub.auth.application.port.out;

import com.davidcreate.jobhub.auth.domain.entity.VerificationCode;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;

import java.util.Optional;
import java.util.UUID;

public interface VerificationCodeRepository {

    VerificationCode save(VerificationCode code);

    Optional<VerificationCode> findOneById(UUID id);

    /**
     * Returns the newest unconsumed, unexpired code for the given user and action,
     * used for the pre-login verify-email path where no verificationId is held by the client.
     */
    Optional<VerificationCode> findActiveByUserAndAction(UUID userId, VerificationAction action);

    /**
     * Marks all unconsumed codes for the given user+action as consumed (sets consumed_at = now).
     * Called before saving a fresh resend code to invalidate prior ones.
     */
    void consumeAllActiveByUserAndAction(UUID userId, VerificationAction action);
}
