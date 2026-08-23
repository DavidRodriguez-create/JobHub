package com.davidcreate.jobhub.notification.domain.port.out;

import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderChannel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CustomReminderRepository {

    CustomReminder save(CustomReminder reminder);

    CustomReminder update(CustomReminder reminder);

    Optional<CustomReminder> findByIdForUser(UUID id, UUID userId);

    List<CustomReminder> findAllForUser(UUID userId, boolean includeFired);

    List<CustomReminder> findAllForUserAndApplication(UUID userId, UUID applicationId, boolean includeFired);

    List<CustomReminder> findDue(Instant now, int limit);

    /**
     * Conditionally flips a SCHEDULED reminder to CANCELLED. Returns true if a row was
     * updated (i.e. it was still SCHEDULED), false if another process already moved it
     * (idempotent no-op for the caller).
     */
    boolean markCancelled(UUID id);

    /**
     * Conditionally flips a SCHEDULED reminder to FIRED. Returns true if a row was
     * updated; false means another scheduler tick already won the race.
     */
    boolean markFired(UUID id, Set<CustomReminderChannel> channelsFired, Instant firedAtUtc);
}
