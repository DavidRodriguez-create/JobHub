package com.davidcreate.jobhub.job.domain.port.in;

import com.davidcreate.jobhub.job.domain.model.JobPost;
import com.davidcreate.jobhub.job.domain.model.JobSearchQuery;

import java.util.List;

public interface SearchJobsUseCase {
    List<JobPost> search(JobSearchQuery query);

    long count(JobSearchQuery query);
}