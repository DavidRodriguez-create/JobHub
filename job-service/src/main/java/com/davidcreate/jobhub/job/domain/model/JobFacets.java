package com.davidcreate.jobhub.job.domain.model;

import java.util.List;

/**
 * Distinct filter values across the entire job-post table, used to populate the
 * job-search filter controls. Counts are table-wide and independent of any
 * active filter. {@code compensationMin}/{@code compensationMax} bound the whole
 * table and may be {@code null} when no postings carry compensation data.
 */
public record JobFacets(
        List<FacetValue> companies,
        List<FacetValue> locations,
        List<FacetValue> languages,
        List<FacetValue> employmentTypes,
        Integer compensationMin,
        Integer compensationMax) {
}
