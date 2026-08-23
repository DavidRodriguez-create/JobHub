package com.davidcreate.jobhub.notification.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Represents a non-terminal application that has had no activity for longer than
 * the configured inactivity window. Returned by the stale-applications gateway
 * for the ghosted-alert scheduler to process.
 */
@Getter
@Builder
public class StaleApplication {

    private final UUID id;
    private final UUID userId;
    private final String jobTitle;
    private final String company;
    private final int daysSinceLastActivity;
}
