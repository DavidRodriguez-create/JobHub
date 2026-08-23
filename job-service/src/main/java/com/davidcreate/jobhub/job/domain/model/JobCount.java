package com.davidcreate.jobhub.job.domain.model;

/**
 * Result of the hybrid job-search count strategy (ADR 0018): the total itself,
 * plus whether it came from the PostgreSQL planner's row estimate ({@code true})
 * rather than an exact {@code COUNT} ({@code false}).
 */
public record JobCount(long value, boolean estimate) {
}
