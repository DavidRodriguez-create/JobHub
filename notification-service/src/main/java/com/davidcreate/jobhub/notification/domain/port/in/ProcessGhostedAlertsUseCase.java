package com.davidcreate.jobhub.notification.domain.port.in;

/**
 * Use-case interface for the daily ghosted-alert run.
 * The scheduler fires this once per day; the implementation queries for stale
 * applications, updates their status to ghosted, writes notifications, and
 * optionally sends an alert email.
 */
public interface ProcessGhostedAlertsUseCase {

    void run();
}
