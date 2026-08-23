package com.davidcreate.jobhub.job.domain.model;

import java.util.List;

/**
 * Distinct filter values used to populate the job-search filter controls.
 * When a {@link JobSearchQuery} is supplied, each group is computed against all
 * active filters <em>except</em> that group's own dimension (drill-down semantics).
 * {@code compensationMin}/{@code compensationMax} bound the range of postings
 * matching every filter except the compensation bounds themselves, and may be
 * {@code null} when no in-scope posting carries compensation data.
 * Omitting all filters reproduces the original table-wide behaviour.
 */
public record JobFacets(
        List<FacetValue> companies,
        List<FacetValue> locations,
        List<FacetValue> languages,
        List<FacetValue> employmentTypes,
        List<FacetValue> careerLevels,
        Integer compensationMin,
        Integer compensationMax) {
}
