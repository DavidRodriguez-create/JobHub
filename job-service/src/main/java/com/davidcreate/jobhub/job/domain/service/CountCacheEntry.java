package com.davidcreate.jobhub.job.domain.service;

import com.davidcreate.jobhub.job.domain.model.JobCount;

import java.time.Duration;
import java.time.Instant;

/**
 * One cached total, paired with the instant it was computed. Freshness is checked
 * against an externally-supplied {@code now} so the boundary is unit-testable with
 * a fake clock, with no real sleep involved (ADR 0018 / AC-331-13).
 */
public record CountCacheEntry(JobCount value, Instant cachedAt) {

    public boolean isFresh(Instant now, Duration ttl) {
        return !now.isAfter(cachedAt.plus(ttl));
    }
}
