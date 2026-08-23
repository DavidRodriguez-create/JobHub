package com.davidcreate.jobhub.notification.domain.port.out;

import com.davidcreate.jobhub.notification.domain.model.UpcomingNextStep;

import java.util.List;

public interface UpcomingNextStepsGateway {

    /**
     * Fetches upcoming next steps across ALL users from application-service, within the
     * given forward window in hours.
     *
     * @throws RuntimeException if the call fails (timeout, 5xx, connection error).
     */
    List<UpcomingNextStep> fetch(int withinHours);
}
