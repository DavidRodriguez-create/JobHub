package com.davidcreate.jobhub.application.application.port.in;

import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;

import java.util.UUID;

public record ListApplicationsQuery(
        UUID callerId,
        ApplicationStatus statusFilter,
        int page,
        int size) {
}
