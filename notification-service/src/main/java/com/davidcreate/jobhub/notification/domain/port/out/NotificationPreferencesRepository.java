package com.davidcreate.jobhub.notification.domain.port.out;

import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferencesRepository {

    Optional<NotificationPreferences> findByUserId(UUID userId);

    NotificationPreferences upsert(NotificationPreferences preferences);

    /**
     * @return the IDs of users with an explicit {@code weekly_digest_email = true} row in
     *         {@code notification.notification_preferences} (BR-1). notification-service has
     *         no cross-schema visibility into {@code auth.user}, so users who have never
     *         created a preferences row (and would default to opted-in per BR-1) cannot be
     *         enumerated by this query alone — see ADR 0008 follow-ups / story #80 handoff notes.
     */
    List<UUID> findWeeklyDigestCandidateUserIds();
}
