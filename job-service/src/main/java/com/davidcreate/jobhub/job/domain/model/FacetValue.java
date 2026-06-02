package com.davidcreate.jobhub.job.domain.model;

/**
 * A distinct filter value and the number of postings carrying it across the
 * whole job-post table.
 */
public record FacetValue(String value, long count) {
}
