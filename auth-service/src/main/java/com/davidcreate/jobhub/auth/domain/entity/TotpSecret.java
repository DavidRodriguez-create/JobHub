package com.davidcreate.jobhub.auth.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class TotpSecret {

    private final UUID id;
    private final UUID userId;
    private final String encryptedSecret;
    private final boolean verified;
    private final OffsetDateTime verifiedAt;
    private final OffsetDateTime createdAt;

    public boolean isActive() {
        return verified;
    }
}
