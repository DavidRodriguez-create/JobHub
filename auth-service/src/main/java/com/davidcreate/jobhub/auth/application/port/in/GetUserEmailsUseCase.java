package com.davidcreate.jobhub.auth.application.port.in;

import java.util.List;
import java.util.UUID;

public interface GetUserEmailsUseCase {

    /**
     * Resolves email addresses for the given user IDs. Users that do not exist or are
     * not email-verified are silently omitted from the result (ADR 0008).
     */
    List<UserEmailResult> getEmails(List<UUID> userIds);
}
