package com.davidcreate.jobhub.job.domain.service;

import com.davidcreate.jobhub.job.domain.model.JobFacets;

import java.time.Duration;
import java.time.Instant;

/**
 * One cached facets payload, paired with the instant it was computed and the
 * crawl-data generation stamp (ADR 0020) it was computed under. Freshness is
 * checked against an externally-supplied {@code now} so the boundary is
 * unit-testable with a fake clock, with no real sleep involved (mirrors
 * {@code CountCacheEntry}, ADR 0018 / AC-331-13). The {@code generation} field
 * lets {@link FacetCache#get(FacetCacheKey, long)} reject a stale-generation
 * entry explicitly, independent of the TTL check.
 */
public record FacetCacheEntry(JobFacets value, Instant cachedAt, long generation) {

    public boolean isFresh(Instant now, Duration ttl) {
        return !now.isAfter(cachedAt.plus(ttl));
    }
}
