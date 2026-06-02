package com.davidcreate.jobhub.auth.application.port.in;

import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RequestVerificationUseCase {

    VerificationResult request(UUID userId, VerificationAction action);

    record VerificationResult(UUID verificationId, OffsetDateTime expiresAt) {
    }
}
