package com.davidcreate.jobhub.job.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class JobSearchQuery {

    private final String keyword;
    private final List<String> locations;
    private final List<String> languages;
    private final List<String> companies;
    private final List<EmploymentType> employmentTypes;
    private final Integer compensationMin;
    private final Integer compensationMax;
    private final PostedWithin postedWithin;
    private final JobSortOrder sort;

    @Builder.Default
    private final int page = 0;

    @Builder.Default
    private final int size = 20;

    public JobSortOrder sortOrDefault() {
        return sort != null ? sort : JobSortOrder.NEWEST;
    }
}
