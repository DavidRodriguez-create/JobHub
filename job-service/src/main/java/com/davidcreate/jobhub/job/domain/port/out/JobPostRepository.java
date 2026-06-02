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

    JobFacets facets();
}