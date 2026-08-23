package com.davidcreate.jobhub.auth.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class TwoFactorChallenge {

    private final UUID id;
    private final UUID userId;
    private final String tokenHash;
    private final OffsetDateTime expiresAt;
    private final OffsetDateTime consumedAt;
    private final OffsetDateTime createdAt;

    public boolean isUsable(OffsetDateTime now) {
        return consumedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }
}
