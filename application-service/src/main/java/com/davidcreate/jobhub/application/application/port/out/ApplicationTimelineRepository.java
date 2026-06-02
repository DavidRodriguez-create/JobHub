package com.davidcreate.jobhub.application.application.port.out;

import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import com.davidcreate.jobhub.application.domain.valueobject.TimelineEntry;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ApplicationTimelineRepository {

    void append(UUID applicationId, ApplicationStatus status, OffsetDateTime occurredAt);

    List<TimelineEntry> findByApplication(UUID applicationId);

    /** Delete all timeline rows belonging to the user's applications (used by DELETE /applications). */
    void removeByUser(UUID userId);

    /** Average days between appliedAt and the first status change, across the user's applications. */
    double avgReplyDays(UUID userId);

    /** Number of the user's applications that ever reached an offer (offered/accepted). */
    long countReachedOffer(UUID userId);
}
