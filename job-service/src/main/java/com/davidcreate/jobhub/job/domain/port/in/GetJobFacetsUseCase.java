package com.davidcreate.jobhub.job.domain.port.in;

import com.davidcreate.jobhub.job.domain.model.JobFacets;
import com.davidcreate.jobhub.job.domain.model.JobSearchQuery;

public interface GetJobFacetsUseCase {
    JobFacets getFacets(JobSearchQuery query);
}
