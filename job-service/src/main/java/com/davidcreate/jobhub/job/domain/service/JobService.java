package com.davidcreate.jobhub.job.domain.service;

import com.davidcreate.jobhub.job.domain.model.CountMode;
import com.davidcreate.jobhub.job.domain.model.JobCount;
import com.davidcreate.jobhub.job.domain.model.JobFacets;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import com.davidcreate.jobhub.job.domain.model.JobSearchQuery;
import com.davidcreate.jobhub.job.domain.port.in.GetJobFacetsUseCase;
import com.davidcreate.jobhub.job.domain.port.in.GetJobUseCase;
import com.davidcreate.jobhub.job.domain.port.in.SearchJobsUseCase;
import com.davidcreate.jobhub.job.domain.port.out.JobPostRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JobService implements SearchJobsUseCase, GetJobUseCase, GetJobFacetsUseCase {

    private final JobPostRepository jobPostRepository;
    private final CountCache countCache;
    private final FacetCache facetCache;
    private final CrawlGenerationStamp crawlGenerationStamp;
    private final CountMode countMode;
    private final long exactThreshold;

    public JobService(JobPostRepository jobPostRepository,
                       CountCache countCache,
                       FacetCache facetCache,
                       CrawlGenerationStamp crawlGenerationStamp,
                       @ConfigProperty(name = "job.search.count.mode", defaultValue = "hybrid") String countMode,
                       @ConfigProperty(name = "job.search.count.exact-threshold", defaultValue = "1000") long exactThreshold) {
        this.jobPostRepository = jobPostRepository;
        this.countCache = countCache;
        this.facetCache = facetCache;
        this.crawlGenerationStamp = crawlGenerationStamp;
        this.countMode = CountMode.fromValue(countMode);
        this.exactThreshold = exactThreshold;
    }

    @Override
    @Transactional
    public List<JobPost> search(JobSearchQuery query) {
        return jobPostRepository.search(query);
    }

    @Override
    @Transactional
    public JobCount count(JobSearchQuery query) {
        CountCacheKey key = CountCacheKey.from(query);
        Optional<JobCount> cached = countCache.get(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        JobCount result = compute(query);
        countCache.put(key, result);
        return result;
    }

    /**
     * ADR 0018 hybrid strategy: {@code mode=exact} never even calls
     * {@link JobPostRepository#estimateCount(JobSearchQuery)} (bounds the legacy
     * path to exactly its old cost); {@code mode=estimate}/{@code hybrid} always
     * ask for the planner estimate first, then {@link CountDecision} decides
     * whether the exact {@code COUNT} is cheap enough to also run.
     */
    private JobCount compute(JobSearchQuery query) {
        if (countMode == CountMode.EXACT) {
            return new JobCount(jobPostRepository.count(query), false);
        }

        long estimate = jobPostRepository.estimateCount(query);
        if (CountDecision.isEstimate(estimate, countMode, exactThreshold)) {
            return new JobCount(estimate, true);
        }
        return new JobCount(jobPostRepository.count(query), false);
    }

    @Override
    @Transactional
    public Optional<JobPost> getById(UUID id) {
        return jobPostRepository.findJobById(id);
    }

    /**
     * ADR 0020: read the current crawl-data generation stamp, then serve the cached
     * facets payload only when its own {@code generation} still matches (a stale
     * generation, or a plain miss, both fall through to a fresh compute + store).
     */
    @Override
    @Transactional
    public JobFacets getFacets(JobSearchQuery query) {
        long generation = crawlGenerationStamp.current();
        FacetCacheKey key = FacetCacheKey.from(query);
        Optional<JobFacets> cached = facetCache.get(key, generation);
        if (cached.isPresent()) {
            return cached.get();
        }

        JobFacets result = jobPostRepository.facets(query);
        facetCache.put(key, result, generation);
        return result;
    }
}
