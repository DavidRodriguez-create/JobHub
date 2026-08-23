package com.davidcreate.jobhub.job.domain.service;

import com.davidcreate.jobhub.job.domain.model.CareerLevel;
import com.davidcreate.jobhub.job.domain.model.EmploymentType;
import com.davidcreate.jobhub.job.domain.model.JobSearchQuery;
import com.davidcreate.jobhub.job.domain.model.PostedWithin;

import java.util.List;

/**
 * Cache key for the job-search total (ADR 0018), derived from the FILTER set
 * only: {@code page}, {@code size}, and {@code sort} are deliberately excluded.
 * The total is a property of which postings match, not of how the matching page
 * is sliced or ordered. Two {@link JobSearchQuery} instances with identical filter
 * fields always produce equal keys regardless of pagination/sort (AC-331-12);
 * differing in any single filter field always produces unequal keys (AC-331-11).
 */
public record CountCacheKey(
        String keyword,
        List<String> locations,
        List<String> languages,
        List<String> companies,
        List<EmploymentType> employmentTypes,
        List<CareerLevel> careerLevels,
        Integer compensationMin,
        Integer compensationMax,
        PostedWithin postedWithin) {

    public static CountCacheKey from(JobSearchQuery query) {
        return new CountCacheKey(
                query.getKeyword(),
                query.getLocations(),
                query.getLanguages(),
                query.getCompanies(),
                query.getEmploymentTypes(),
                query.getCareerLevels(),
                query.getCompensationMin(),
                query.getCompensationMax(),
                query.getPostedWithin());
    }
}
