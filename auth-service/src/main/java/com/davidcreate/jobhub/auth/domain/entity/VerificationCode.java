package com.davidcreate.jobhub.auth.domain.entity;

import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class VerificationCode {

    private final UUID id;
    private final UUID userId;
    private final VerificationAction action;
    private final String codeHash;
    private final OffsetDateTime expiresAt;
    private final OffsetDateTime consumedAt;
    private final OffsetDateTime createdAt;

    public boolean isUsable(OffsetDateTime now) {
        return consumedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }
}
