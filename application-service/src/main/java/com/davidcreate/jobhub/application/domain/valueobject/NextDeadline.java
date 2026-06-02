package com.davidcreate.jobhub.application.domain.valueobject;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The earliest upcoming next-step date across a user's active applications, with the
 * application it belongs to. Used for the dashboard 'Respond by …' label.
 */
public record NextDeadline(LocalDate date, UUID applicationId) {
}
