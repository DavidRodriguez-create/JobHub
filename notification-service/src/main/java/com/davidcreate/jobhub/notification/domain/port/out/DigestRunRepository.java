package com.davidcreate.jobhub.notification.domain.port.out;

import com.davidcreate.jobhub.notification.domain.model.DigestRun;

import java.util.UUID;

public interface DigestRunRepository {

    /**
     * @return {@code true} if a {@code digest_run} row with {@code status = SENT} and
     *         {@code sentAt} within the current ISO week (Monday 00:00 UTC – Sunday 23:59:59 UTC)
     *         already exists for this user.
     */
    boolean hasSentThisWeek(UUID userId);

    DigestRun save(DigestRun digestRun);
}
