
package com.davidcreate.jobhub.crawler.domain.model;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class PullResult {

    private final boolean success;
    private final List<JobPost> jobs;
    private final String errorReason;
    private final Integer httpStatus;

    private PullResult(boolean success, List<JobPost> jobs, String errorReason, Integer httpStatus) {
        this.success = success;
        this.jobs = jobs != null ? Collections.unmodifiableList(jobs) : Collections.emptyList();
        this.errorReason = errorReason;
        this.httpStatus = httpStatus;
    }

    public static PullResult success(List<JobPost> jobs) {
        return new PullResult(true, jobs, null, null);
    }

    public static PullResult failure(String reason) {
        return new PullResult(false, null, reason, null);
    }

    public static PullResult failure(String reason, int httpStatus) {
        return new PullResult(false, null, reason, httpStatus);
    }

    public int getJobCount() {
        return jobs.size();
    }
}