package com.davidcreate.jobhub.application.domain.entity;

import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;

import java.util.Map;

/**
 * Per-month application counts broken down by status, for the dashboard trend chart.
 * {@code byStatus} omits statuses with no applications in the month.
 */
public record MonthlyStats(int year, int month, Map<ApplicationStatus, Long> byStatus) {
}
