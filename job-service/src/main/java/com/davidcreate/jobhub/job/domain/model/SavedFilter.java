package com.davidcreate.jobhub.job.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A named job-search preset owned by a user. {@code filtersJson} holds the
 * serialised FilterValues payload (its field names mirror the GET /jobs query
 * params); the REST layer owns (de)serialisation so the domain stays opaque to it.
 */
@Getter
@Builder(toBuilder = true)
public class SavedFilter {

    private final UUID id;
    private final UUID userId;
    private final String name;
    private final String filtersJson;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
}
