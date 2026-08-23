package com.davidcreate.jobhub.auth.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class UserIdentity {

    private final UUID id;
    private final UUID userId;
    private final String provider;
    private final String providerUserId;
    private final String email;
    private final OffsetDateTime createdAt;
}
