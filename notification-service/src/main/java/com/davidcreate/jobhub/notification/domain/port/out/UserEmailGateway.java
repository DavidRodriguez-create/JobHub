package com.davidcreate.jobhub.notification.domain.port.out;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface UserEmailGateway {

    /**
     * Batch-fetches email addresses for the given user IDs from auth-service.
     * Users that do not exist or are not email-verified are silently omitted
     * from the returned map.
     *
     * @throws RuntimeException if the call fails (timeout, 5xx, connection error).
     */
    Map<UUID, String> fetchEmails(Set<UUID> userIds);
}
