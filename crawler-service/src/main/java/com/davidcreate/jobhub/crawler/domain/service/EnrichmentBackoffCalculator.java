package com.davidcreate.jobhub.crawler.domain.service;

/**
 * Story #537: pure delay math for the enrichment scheduled-pass failure backoff.
 * No framework annotations, no Clock dependency: the caller supplies every input.
 */
public final class EnrichmentBackoffCalculator {

    private EnrichmentBackoffCalculator() {
    }

    /**
     * @param consecutiveFailures total consecutive failed passes so far (1 = the
     *                             most recent pass was the first consecutive failure)
     * @param stepMinutes         additive step per consecutive failure; {@code <= 0} disables backoff
     * @param maxMinutes          cap on the delay; {@code <= 0} disables backoff
     * @return the delay in minutes before the next attempt, 0 when there is nothing to back off from
     */
    public static int nextDelayMinutes(int consecutiveFailures, int stepMinutes, int maxMinutes) {
        if (consecutiveFailures <= 0 || stepMinutes <= 0 || maxMinutes <= 0) {
            return 0;
        }
        long raw = (long) consecutiveFailures * stepMinutes;
        return (int) Math.min(raw, maxMinutes);
    }
}
