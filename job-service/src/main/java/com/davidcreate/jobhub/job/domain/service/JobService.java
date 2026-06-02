package com.davidcreate.jobhub.job.domain.service;

import com.davidcreate.jobhub.job.domain.model.JobFacets;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import com.davidcreate.jobhub.job.domain.model.JobSearchQuery;
import com.davidcreate.jobhub.job.domain.port.in.GetJobFacetsUseCase;
import com.davidcreate.jobhub.job.domain.port.in.GetJobUseCase;
import com.davidcreate.jobhub.job.domain.port.in.SearchJobsUseCase;
import com.davidcreate.jobhub.job.domain.port.out.JobPostRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JobService implements SearchJobsUseCase, GetJobUseCase, GetJobFacetsUseCase {

    private final JobPostRepository jobPostRepository;

    public JobService(JobPostRepository jobPostRepository) {
        this.jobPostRepository = jobPostRepository;
    }

    @Override
    @Transactional
    public List<JobPost> search(JobSearchQuery query) {
        return jobPostRepository.search(query);
    }

    @Override
    @Transactional
    public long count(JobSearchQuery query) {
        return jobPostRepository.count(query);
    }

    @Override
    @Transactional
    public Optional<JobPost> getById(UUID id) {
        return jobPostRepository.findJobById(id);
    }

    @Override
    @Transactional
    public JobFacets getFacets() {
        return jobPostRepository.facets();
    }
}
