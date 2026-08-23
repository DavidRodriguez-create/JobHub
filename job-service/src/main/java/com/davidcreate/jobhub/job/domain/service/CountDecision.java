package com.davidcreate.jobhub.job.domain.service;

import com.davidcreate.jobhub.job.domain.model.CountMode;

/**
 * Pure {@code (plannerEstimate, mode, threshold) -> EXACT | ESTIMATE} decision
 * (ADR 0018). Deliberately has no dependency on {@code JobPostRepository} or the
 * persistence layer so it stays trivially unit-testable.
 */
public final class CountDecision {

    private CountDecision() {}

    /**
     * @return {@code true} when the estimate branch should be taken, {@code false}
     *         when the exact {@code COUNT} branch should be taken.
     */
    public static boolean isEstimate(long plannerEstimate, CountMode mode, long threshold) {
        return switch (mode) {
            case EXACT -> false;
            case ESTIMATE -> true;
            case HYBRID -> plannerEstimate > threshold;
        };
    }
}
