package com.davidcreate.jobhub.notification.domain.port.out;

import com.davidcreate.jobhub.notification.domain.model.DigestJob;
import com.davidcreate.jobhub.notification.domain.model.JobSearchQuery;

import java.util.List;

public interface JobSearchGateway {

    /**
     * Searches job-service for jobs matching the given query.
     *
     * @throws RuntimeException if the call fails (timeout, 5xx, connection error).
     */
    List<DigestJob> search(JobSearchQuery query);
}
