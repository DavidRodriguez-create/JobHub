package com.davidcreate.jobhub.application.application.port.in;

import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;

import java.util.UUID;

public record UpdateApplicationStatusCommand(
        UUID callerId,
        UUID applicationId,
        ApplicationStatus status) {
}
