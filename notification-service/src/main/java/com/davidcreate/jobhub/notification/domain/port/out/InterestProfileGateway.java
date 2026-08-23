package com.davidcreate.jobhub.notification.domain.port.out;

import com.davidcreate.jobhub.notification.domain.model.InterestProfile;

import java.util.UUID;

public interface InterestProfileGateway {

    /**
     * Fetches the interest profile for a single user from application-service.
     * Returns a profile with empty lists if the user has no application history.
     *
     * @throws RuntimeException if the call fails (timeout, 5xx, connection error).
     */
    InterestProfile fetch(UUID userId);
}
