package com.davidcreate.jobhub.application.application.port.in;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Partial update of an application's editable fields. A {@code null} scalar means "leave
 * unchanged". {@code nextStepProvided} distinguishes "no nextStep in the request" from
 * "nextStep present" — when {@code true} the three nextStep fields are applied as given
 * (any of them may be {@code null} to clear that part of the step).
 */
public record UpdateApplicationCommand(
        UUID callerId,
        UUID applicationId,
        String notes,
        OffsetDateTime appliedAt,
        String contact,
        String portalUrl,
        boolean nextStepProvided,
        String nextStepLabel,
        LocalDate nextStepDate,
        OffsetDateTime nextStepReminderAt) {
}
