package com.davidcreate.jobhub.crawler.domain.exception;

/**
 * Transient exhaustion of the enrichment provider chain: every provider either
 * threw (transport error, 429, timeout) or was mid-cooldown, so no provider was
 * usable for this call. Distinct from a provider being reachable but producing
 * unusable output, which is a genuine per-posting failure and keeps the existing
 * {@link IllegalStateException} path. Callers should not blame the posting for a
 * transient exception: {@code EnrichmentService} does not increment its retry
 * counter and stops the batch early instead of burning through every pending row.
 */
public class EnrichmentUnavailableException extends RuntimeException {

    public EnrichmentUnavailableException(String message) {
        super(message);
    }
}
