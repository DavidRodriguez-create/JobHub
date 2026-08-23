package com.davidcreate.jobhub.auth.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class BackupCode {

    private final UUID id;
    private final UUID totpSecretId;
    private final String codeHash;
    private final OffsetDateTime consumedAt;
    private final OffsetDateTime createdAt;

    public boolean isUsable() {
        return consumedAt == null;
    }
}
