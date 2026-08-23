package com.davidcreate.jobhub.auth.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class User {

    private final UUID id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String passwordHash;
    private final boolean emailVerified;
    private final OffsetDateTime emailVerifiedAt;
    private final boolean twoFactorEnabled;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
}
