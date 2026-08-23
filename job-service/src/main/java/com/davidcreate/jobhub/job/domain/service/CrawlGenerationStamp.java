package com.davidcreate.jobhub.job.domain.service;

import com.davidcreate.jobhub.job.domain.port.out.JobPostRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;

/**
 * TTL-guarded read of the crawl-data generation stamp (ADR 0020): the newest of
 * "last time any posting was (re-)seen by a crawl" and "last time any posting
 * was enriched", read cheaply from {@code crawler.job_post} via
 * {@link JobPostRepository#facetDataVersion()}. Re-reads at most once per
 * {@code job.search.facets.stamp.ttl}, so facet traffic never turns into a
 * per-request {@code MAX} storm.
 *
 * <p>The "should I re-read?" test ({@link #isReadDue(Instant, Instant, Duration)})
 * is a pure function of {@code (lastReadInstant, now, ttl)}, unit-testable
 * without a real clock. {@link #current(Instant)} takes {@code now} as a
 * parameter for the same reason (no clock seam in production code whose only
 * purpose is testing); {@link #current()} is the production entry point.
 *
 * <p>Fail-soft on a repository read error: returns the last-known stamp rather
 * than propagating, so a stamp-read failure never fails the facets endpoint
 * (ADR 0020 "serve within the entry TTL"). On a cold-start read error (no
 * last-known stamp yet), returns {@code 0L}, matching
 * {@link JobPostRepository#facetDataVersion()}'s own empty-table convention.
 */
@ApplicationScoped
public class CrawlGenerationStamp {

    private static final Logger LOG = Logger.getLogger(CrawlGenerationStamp.class);

    private final JobPostRepository jobPostRepository;
    private final Duration ttl;

    private volatile long lastValue = 0L;
    private volatile Instant lastRead;

    public CrawlGenerationStamp(JobPostRepository jobPostRepository,
            @ConfigProperty(name = "job.search.facets.stamp.ttl", defaultValue = "PT10S") Duration ttl) {
        this.jobPostRepository = jobPostRepository;
        this.ttl = ttl;
    }

    public long current() {
        return current(Instant.now());
    }

    public long current(Instant now) {
        if (lastRead != null && !isReadDue(lastRead, now, ttl)) {
            return lastValue;
        }
        try {
            lastValue = jobPostRepository.facetDataVersion();
        } catch (RuntimeException e) {
            LOG.warnf(e, "facetDataVersion() read failed; serving last-known generation stamp (%d)", lastValue);
        }
        lastRead = now;
        return lastValue;
    }

    public static boolean isReadDue(Instant lastRead, Instant now, Duration ttl) {
        return now.isAfter(lastRead.plus(ttl));
    }
}
