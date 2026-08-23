package com.davidcreate.jobhub.job.domain.port.out;

import com.davidcreate.jobhub.job.domain.model.JobFacets;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import com.davidcreate.jobhub.job.domain.model.JobSearchQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobPostRepository {
    Optional<JobPost> findJobById(UUID id);

    List<JobPost> search(JobSearchQuery query);

    long count(JobSearchQuery query);

    /**
     * A PostgreSQL planner row estimate for {@code query}'s filter predicates
     * (plan-only, no row scan/execution), ADR 0018. Used by the hybrid count
     * strategy to decide whether an exact {@link #count(JobSearchQuery)} is cheap
     * enough to run, without ever paying more to count a page than to fetch it.
     */
    long estimateCount(JobSearchQuery query);

    JobFacets facets(JobSearchQuery query);

    /**
     * Crawl-data generation stamp (ADR 0020): epoch-millis of
     * {@code GREATEST(MAX(last_seen_at), MAX(enriched_at))} over
     * {@code crawler.job_post} ({@code 0} on an empty table). Captures both a
     * crawl inserting/re-seeing a posting ({@code last_seen_at}) and the
     * separate async enrichment pass that backfills the exact fields facets
     * group by ({@code enriched_at}); a pull/trigger-completion timestamp
     * would miss the latter. Used by {@link com.davidcreate.jobhub.job.domain.service.CrawlGenerationStamp}
     * to TTL-guard the facets cache's invalidation signal.
     */
    long facetDataVersion();
}