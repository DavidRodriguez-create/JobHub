package com.davidcreate.jobhub.application.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class UserJobPost {

    private final UUID id;
    private final UUID userId;
    private final String title;
    private final String company;
    private final String url;
    private final String location;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
}
